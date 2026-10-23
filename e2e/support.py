"""Ferramentas do teste de ponta a ponta: adb, leitura da tela e atalhos para os emuladores."""
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
SDK = os.environ.get("ANDROID_HOME") or os.path.join(os.path.expanduser("~"), "AppData", "Local", "Android", "Sdk")
ADB = os.environ.get("ADB") or os.path.join(SDK, "platform-tools", "adb")
APK = os.path.join(HERE, "..", "app", "build", "outputs", "apk", "localTest", "app-localTest.apk")
# Pasta do fcm-server com node_modules (o reset do Mongo usa o mongoose de lá).
FCM_SERVER = os.environ.get("FCM_SERVER_DIR", os.path.join(HERE, "..", "..", "fcm-server"))
MONGO_URI = os.environ.get("E2E_MONGO_URI", "mongodb://127.0.0.1:27027/churrasco_test_e2e?replicaSet=testset")
SHOTS = os.path.join(HERE, "shots")
PKG = "com.longynus.churrasco"
PROJECT = "demo-churrasco"
AUTH = "http://127.0.0.1:9099"
DB = "http://127.0.0.1:9000"
API = "http://127.0.0.1:3000"
APP_VERSION = "2000"
PASSWORD = "teste-local-123"  # só existe no emulador do Firebase

results = []


def adb(*args, check=True):
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", check=check).stdout


def http(method, url, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("X-App-Version", APP_VERSION)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw or b"null")
        except ValueError:
            return e.code, {"raw": raw.decode(errors="replace")}


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail))
    print(("PASS " if ok else "FAIL ") + name + (f" -- {detail}" if detail and not ok else ""), flush=True)
    return ok


def finish():
    failed = [r for r in results if not r[1]]
    print(f"\n{len(results) - len(failed)}/{len(results)} passaram")
    sys.exit(1 if failed else 0)


def shot(name):
    os.makedirs(SHOTS, exist_ok=True)
    with open(os.path.join(SHOTS, f"{name}.png"), "wb") as f:
        f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)


# --- Tela ----------------------------------------------------------------

def dump():
    for _ in range(6):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
        if "<hierarchy" in xml:
            root = ET.fromstring(xml[xml.index("<hierarchy"):])
            if dismiss_system_dialog(root):
                continue
            return root
        time.sleep(1)
    raise RuntimeError("uiautomator dump falhou")


def dismiss_system_dialog(root):
    """Emuladores lentos às vezes mostram "X isn't responding" de apps do sistema."""
    texts = [n.get("text") or "" for n in root.iter("node")]
    if not any("isn't responding" in t or "não está respondendo" in t for t in texts):
        return False
    for n in root.iter("node"):
        if n.get("text") in ("Wait", "Aguardar"):
            a, b, c, d = map(int, re.findall(r"\d+", n.get("bounds")))
            adb("shell", "input", "tap", str((a + c) // 2), str((b + d) // 2))
            time.sleep(2)
            return True
    return False


def nodes(root):
    return list(root.iter("node"))


def bounds(n):
    return list(map(int, re.findall(r"\d+", n.get("bounds"))))


def find(rid=None, text=None, contains=None, desc=None, hint=None, root=None):
    root = root if root is not None else dump()
    for n in nodes(root):
        if hint is not None and not (n.get("class", "").endswith("EditText") and hint in (n.get("hint") or "")):
            continue
        if rid and not n.get("resource-id", "").endswith(":id/" + rid):
            continue
        if text is not None and n.get("text") != text:
            continue
        if contains is not None and contains not in (n.get("text") or "") and contains not in (n.get("content-desc") or ""):
            continue
        if desc is not None and desc not in (n.get("content-desc") or ""):
            continue
        a, b, c, d = bounds(n)
        if c <= a or d <= b:
            continue
        return n
    return None


def wait_for(timeout=40, **kw):
    end = time.time() + timeout
    while time.time() < end:
        n = find(**kw)
        if n is not None:
            return n
        time.sleep(1)
    return None


def screen_text():
    return " | ".join(n.get("text") for n in nodes(dump()) if n.get("text"))


def tap(n):
    if n is None:
        raise AssertionError("elemento não encontrado na tela: " + screen_text()[:400])
    a, b, c, d = bounds(n)
    adb("shell", "input", "tap", str((a + c) // 2), str((b + d) // 2))
    time.sleep(1)


def usable_area(root):
    """Faixa vertical visível entre a barra do topo e a barra/navegação de baixo."""
    upper, lower = 200, 2300
    toolbar = find(rid="topToolbar", root=root)
    if toolbar is not None:
        upper = bounds(toolbar)[3]
    for rid in ("bottomBar", "bottomNavigation"):
        bar = find(rid=rid, root=root)
        if bar is not None and bounds(bar)[1] > upper + 200:
            lower = min(lower, bounds(bar)[1])
    kb = keyboard_top()
    if kb is not None:
        lower = min(lower, kb)
    return upper, lower


def scroll_to(timeout=60, **kw):
    """Rola até o elemento ficar inteiro na área útil. Se ele não está na tela, volta ao
    topo de uma vez e desce procurando (as telas longas ficariam lentas de outro jeito)."""
    end = time.time() + timeout
    went_to_top = False
    last = None
    while time.time() < end:
        root = dump()
        n = find(root=root, **kw)
        if n is not None:
            top, bottom = bounds(n)[1], bounds(n)[3]
            upper, lower = usable_area(root)
            if top >= upper and bottom <= lower:
                return n
            step = min(500, (lower - upper) // 2)
            if top < upper:
                swipe(upper + 60, upper + 60 + step, ms=900)
            else:
                swipe(lower - 60, lower - 60 - step, ms=900)
        elif not went_to_top:
            upper, lower = usable_area(root)
            for _ in range(6):
                swipe(upper + 40, lower - 40, ms=120)
            went_to_top = True
        else:
            snapshot = " | ".join(x.get("text") for x in nodes(root) if x.get("text"))
            if snapshot == last:
                return None  # chegou ao fim sem achar
            last = snapshot
            upper, lower = usable_area(root)
            # Arrasto lento (sem "fling"): avança ~60% da tela e não pula conteúdo.
            swipe(lower - 60, lower - 60 - int((lower - upper) * 0.6), ms=900)
        time.sleep(0.6)
    return None


def swipe(y_from, y_to, ms=300):
    # Pela borda esquerda: fora de cartões e do mapa, que pegariam o gesto.
    adb("shell", "input", "swipe", "30", str(y_from), "30", str(y_to), str(ms))


def type_text(value):
    adb("shell", "input", "text", value.replace(" ", "%s"))
    time.sleep(0.5)
    hide_keyboard()


def keyboard_top():
    """Topo do teclado na tela, ou None se ele estiver fechado."""
    out = adb("shell", "dumpsys", "window", check=False)
    for top, bottom in re.findall(r"type=ime frame=\[\d+,(\d+)\]\[\d+,(\d+)\] visibleFrame=\[[^\]]*\]\[[^\]]*\] visible=true", out):
        if int(bottom) - int(top) > 100:
            return int(top)
    return None


def hide_keyboard():
    # Nada a fazer: o "voltar" do adb fecha a tela em Androids novos mesmo com o
    # teclado aberto. As buscas tratam o teclado como borda de baixo (usable_area).
    pass

def fill(node, value):
    tap(node)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("shell", "input", "keyevent", *(["KEYCODE_DEL"] * 60))
    type_text(value)


def type_into(rid, value):
    fill(wait_for(rid=rid), value)


def tap_text(label, timeout=60, scroll=True):
    node = scroll_to(text=label, timeout=timeout) if scroll else wait_for(text=label, timeout=timeout)
    tap(node)


def dismiss_dialog():
    for label in ("OK", "Fechar"):
        node = find(text=label)
        if node is not None:
            tap(node)
            return


def launch():
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")


def install_fresh():
    adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "0")
    # Sem diálogos de "não está respondendo" de apps do sistema no emulador de teste.
    adb("shell", "settings", "put", "global", "hide_error_dialogs", "1", check=False)
    adb("uninstall", PKG, check=False)
    adb("install", "-r", "-g", APK)


# --- Emuladores do Firebase e API ---------------------------------------------

def db_get(path):
    req = urllib.request.Request(f"{DB}/{path}.json?ns={PROJECT}")
    req.add_header("Authorization", "Bearer owner")
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.loads(r.read())


def reset_state():
    """Começa do zero: contas do Auth, Realtime Database e Mongo de teste."""
    http("DELETE", f"{AUTH}/emulator/v1/projects/{PROJECT}/accounts")
    req = urllib.request.Request(f"{DB}/.json?ns={PROJECT}", data=b"null", method="PUT")
    req.add_header("Authorization", "Bearer owner")
    urllib.request.urlopen(req, timeout=15).read()
    mongo(".then(m=>m.connection.dropDatabase())")


def mongo(chain):
    subprocess.run(["node", "-e", f"require('mongoose').connect('{MONGO_URI}'){chain}.then(()=>process.exit(0))"],
                   cwd=FCM_SERVER, check=True)


def verify_email(email):
    _, body = http("GET", f"{AUTH}/emulator/v1/projects/{PROJECT}/oobCodes")
    links = [c["oobLink"] for c in body["oobCodes"] if c["email"] == email and c["requestType"] == "VERIFY_EMAIL"]
    if not links:
        return False
    urllib.request.urlopen(links[-1], timeout=15).read()
    return True


def sign_in(email):
    _, r = http("POST", f"{AUTH}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo",
                {"email": email, "password": PASSWORD, "returnSecureToken": True})
    return r["idToken"], r["localId"]


def api_user(email, name, uid=None):
    """Cria, confirma e registra um usuário só pela API. Devolve (token, uid)."""
    body = {"email": email, "password": PASSWORD, "emailVerified": True}
    if uid:
        body["localId"] = uid
    req = urllib.request.Request(f"{AUTH}/identitytoolkit.googleapis.com/v1/projects/{PROJECT}/accounts",
                                 data=json.dumps(body).encode(), method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", "Bearer owner")
    urllib.request.urlopen(req, timeout=15).read()
    token, local_id = sign_in(email)
    status, _ = http("POST", f"{API}/users/register", {"displayName": name}, token)
    assert status == 201, f"cadastro de {name}: {status}"
    return token, local_id


def befriend(token_a, email_a, token_b, email_b):
    http("POST", f"{API}/friends", {"email": email_b}, token_a)
    http("POST", f"{API}/friends", {"email": email_a}, token_b)


def sao_paulo(minutes):
    sp = datetime.now(timezone.utc) + timedelta(hours=-3, minutes=minutes)
    return sp.strftime("%d/%m/%Y"), sp.strftime("%H:%M")


def event(event_id, token):
    return http("GET", f"{API}/churrascos/{event_id}", token=token)[1]["churrasco"]
