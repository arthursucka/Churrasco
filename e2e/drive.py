"""Teste de ponta a ponta do Churrasco (build localTest) no emulador Android.

Precisa de: emuladores do Firebase (auth 9099, database 9000, projeto demo-churrasco),
backend em CHURRASCO_LOCAL_TEST=1 na porta 3000 e um emulador Android conectado.
"""
import json, os, re, subprocess, sys, time, urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
SDK = os.environ.get("ANDROID_HOME") or os.path.join(os.path.expanduser("~"), "AppData", "Local", "Android", "Sdk")
ADB = os.path.join(SDK, "platform-tools", "adb")
APK = os.path.join(HERE, "..", "app", "build", "outputs", "apk", "localTest", "app-localTest.apk")
# Pasta do fcm-server com node_modules (o reset do Mongo usa o mongoose de lá).
FCM_SERVER = os.environ.get("FCM_SERVER_DIR", os.path.join(HERE, "..", "..", "fcm-server"))
PKG = "com.longynus.churrasco"
PROJECT = "demo-churrasco"
AUTH = "http://127.0.0.1:9099"
DB = "http://127.0.0.1:9000"
API = "http://127.0.0.1:3000"
SHOTS = os.path.join(HERE, "shots")
PASSWORD = "teste-local-123"  # só existe no emulador do Firebase
results = []


def adb(*args, check=True):
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", check=check).stdout


def http(method, url, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail))
    print(("PASS " if ok else "FAIL ") + name + (f" -- {detail}" if detail else ""), flush=True)


def shot(name):
    os.makedirs(SHOTS, exist_ok=True)
    with open(os.path.join(SHOTS, f"{name}.png"), "wb") as f:
        f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)


def dump():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
        if xml.strip().startswith("<?xml"):
            return ET.fromstring(xml[xml.index("<hierarchy"):])
        time.sleep(1)
    raise RuntimeError("uiautomator dump falhou")


def nodes(root):
    return list(root.iter("node"))


def find(rid=None, text=None, contains=None, root=None):
    root = root if root is not None else dump()
    for n in nodes(root):
        if rid and not n.get("resource-id", "").endswith(":id/" + rid):
            continue
        if text is not None and n.get("text") != text:
            continue
        if contains is not None and contains not in (n.get("text") or ""):
            continue
        if n.get("visible-to-user", "true") == "false":
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


def center(n):
    a, b, c, d = map(int, re.findall(r"\d+", n.get("bounds")))
    return (a + c) // 2, (b + d) // 2


def tap(n):
    x, y = center(n)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1)


def scroll_to(timeout=20, **kw):
    """Rola até o elemento ficar inteiro na tela (fora da barra do topo e da de baixo)."""
    end = time.time() + timeout
    while time.time() < end:
        n = find(**kw)
        if n is not None:
            top, bottom = map(int, re.findall(r"\d+", n.get("bounds"))[1::2])
            if top >= 300 and bottom <= 2210:
                return n
            if top < 300:
                adb("shell", "input", "swipe", "540", "900", "540", "1500", "300")
            else:
                adb("shell", "input", "swipe", "540", "1800", "540", "1200", "300")
        else:
            adb("shell", "input", "swipe", "540", "1800", "540", "900", "300")
        time.sleep(1)
    return None


def type_into(rid, value):
    field = wait_for(rid=rid)
    tap(field)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(40):
        adb("shell", "input", "keyevent", "KEYCODE_DEL")
    adb("shell", "input", "text", value.replace(" ", "%s"))
    hide_keyboard()


def hide_keyboard():
    # O teclado virtual fica desligado (ver main), então não há o que fechar;
    # apertar BACK aqui fecharia a tela.
    time.sleep(0.5)


def dismiss_dialog():
    ok = find(text="OK")
    if ok is not None:
        tap(ok)


def screen_text():
    return " | ".join(n.get("text") for n in nodes(dump()) if n.get("text"))


# --- Helpers dos emuladores do Firebase ---------------------------------

def verify_email(email):
    _, body = http("GET", f"{AUTH}/emulator/v1/projects/{PROJECT}/oobCodes")
    links = [c["oobLink"] for c in body["oobCodes"] if c["email"] == email and c["requestType"] == "VERIFY_EMAIL"]
    if not links:
        return False
    urllib.request.urlopen(links[-1], timeout=15).read()
    return True


def api_user(email, name):
    """Cria, confirma e registra um usuário só pela API (convidados do teste)."""
    _, r = http("POST", f"{AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo",
                {"email": email, "password": PASSWORD, "returnSecureToken": True})
    http("POST", f"{AUTH}/identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=demo",
         {"requestType": "VERIFY_EMAIL", "idToken": r["idToken"]})
    verify_email(email)
    token = sign_in(email)
    status, _ = http("POST", f"{API}/users/register", {"username": name, "displayName": name}, token)
    return token, status


def sign_in(email):
    _, r = http("POST", f"{AUTH}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo",
                {"email": email, "password": PASSWORD, "returnSecureToken": True})
    return r["idToken"]


def sao_paulo_now_plus(minutes):
    sp = datetime.now(timezone.utc) + timedelta(hours=-3, minutes=minutes)
    return sp.strftime("%d/%m/%Y"), sp.strftime("%H:%M")


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
    subprocess.run(["node", "-e",
                    "require('mongoose').connect('mongodb://127.0.0.1:27027/churrasco_test_e2e?replicaSet=testset')"
                    ".then(m=>m.connection.dropDatabase()).then(()=>process.exit(0))"],
                   cwd=FCM_SERVER, check=True)


# --- Roteiro ------------------------------------------------------------

def main():
    org_email = "organizador@churrasco.test"
    reset_state()

    # Teclado virtual desligado: os botões não ficam escondidos atrás dele.
    adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "0")
    adb("uninstall", PKG, check=False)
    adb("install", "-r", "-g", APK)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")

    # 1. Criar conta
    check("tela de login aparece", wait_for(rid="btnCreateAccount") is not None)
    shot("01-login")
    type_into("edtEmail", org_email)
    type_into("edtPassword", PASSWORD)
    tap(find(rid="btnCreateAccount"))
    check("pede confirmação do e-mail", wait_for(rid="btnCheckVerified") is not None)
    check("mostra o e-mail na mensagem", find(contains=org_email) is not None)
    shot("02-confirmar-email")

    # 2. "Já confirmei" sem confirmar continua na mesma etapa
    tap(find(rid="btnCheckVerified"))
    time.sleep(3)
    check("sem confirmar, continua pedindo confirmação", wait_for(rid="btnCheckVerified", timeout=10) is not None)

    # 3. Confirmar pelo link e seguir
    check("e-mail de confirmação foi enviado", verify_email(org_email))
    tap(find(rid="btnCheckVerified"))
    check("pede o nome no grupo", wait_for(rid="btnSaveProfile") is not None)
    shot("03-nome")
    type_into("edtUserName", "Organizador")
    tap(find(rid="btnSaveProfile"))
    check("entra na tela principal", wait_for(contains="Oi, Organizador") is not None, screen_text()[:200])
    shot("04-principal")

    # 4. Convidado e churrasco criados pela API, começando em 30 min (São Paulo)
    guest_token, status = api_user("convidado@churrasco.test", "Convidado")
    check("convidado registrado pela API", status == 201, f"status {status}")
    org_token = sign_in(org_email)
    date, hour = sao_paulo_now_plus(30)
    status, body = http("POST", f"{API}/churrascos", {
        "churrascoDate": date, "hora": hour, "local": "Casa do teste",
        "fornecidos": ["Carvão"], "invitedUsers": ["Convidado"], "userName": "Organizador"}, org_token)
    check("churrasco criado", status == 201, f"{status} {body}")
    churrasco_id = body.get("id")
    status, _ = http("POST", f"{API}/churrascos/{churrasco_id}/confirm-presenca",
                     {"name": "Convidado", "selectedItems": ["Gelo"]}, guest_token)
    check("convidado confirmou presença", status == 200, f"status {status}")
    http("POST", f"{API}/churrascos/{churrasco_id}/messages", {"text": "Levo o gelo!"}, guest_token)

    # 5. Abrir o churrasco no app
    # As telas não são exportadas: navega como a pessoa faria.
    tap(wait_for(rid="btnActiveChurrascos"))
    check("lista de ativos mostra o churrasco", wait_for(contains="Casa do teste", timeout=40) is not None)
    shot("05a-ativos")
    tap(wait_for(contains="Casa do teste"))
    check("detalhes mostram o organizador", wait_for(contains="organizador", timeout=40) is not None)
    shot("05-detalhes")
    check("chat lê a mensagem do convidado (regras do banco com login)",
          scroll_to(contains="Levo o gelo!") is not None)

    # 6. Mandar mensagem pelo app
    type_into("edtMessage", "Fechado, ate mais tarde")
    tap(scroll_to(rid="btnSend"))
    time.sleep(3)
    msgs = db_get(f"churrascos/{churrasco_id}/messages")
    texts = [m["text"] for m in (msgs or {}).values()]
    check("mensagem do app chegou no banco", "Fechado, ate mais tarde" in texts, str(texts))
    check("mensagem do app aparece no chat", scroll_to(contains="Fechado, ate mais tarde") is not None)
    shot("06-chat")

    # 7. Compartilhar localização
    adb("emu", "geo", "fix", "-46.6333", "-23.5505", check=False)
    share = scroll_to(rid="btnShareLocation")
    tap(share)
    time.sleep(8)
    adb("emu", "geo", "fix", "-46.6340", "-23.5510", check=False)
    time.sleep(5)
    locs = db_get(f"churrascos/{churrasco_id}/locations")
    loc = next(iter((locs or {}).values()), {})
    check("localização enviada com sessão", "latitude" in loc and loc.get("sessionId"), str(loc)[:200])
    check("botão vira 'Parar compartilhamento'", find(text="Parar compartilhamento") is not None)
    shot("07-localizacao")
    tap(find(text="Parar compartilhamento"))
    time.sleep(3)
    locs = db_get(f"churrascos/{churrasco_id}/locations")
    check("parar remove a localização do servidor", not locs, str(locs)[:200])

    # 8. Sair da conta
    tap(wait_for(rid="action_home"))
    tap(scroll_to(rid="btnSwitchUser"))
    tap(wait_for(text="Sair"))
    check("sair volta para o login", wait_for(rid="btnSignIn") is not None)

    # 9. Entrar de novo com a mesma conta
    type_into("edtEmail", org_email)
    type_into("edtPassword", PASSWORD)
    tap(find(rid="btnSignIn"))
    check("login com e-mail e senha volta ao perfil", wait_for(contains="Oi, Organizador") is not None)

    # 10. Senha errada
    tap(scroll_to(rid="btnSwitchUser"))
    tap(wait_for(text="Sair"))
    wait_for(rid="btnSignIn")
    type_into("edtEmail", org_email)
    type_into("edtPassword", "senha-errada")
    tap(find(rid="btnSignIn"))
    check("senha errada mostra mensagem clara", wait_for(contains="incorretos", timeout=20) is not None)
    shot("08-senha-errada")
    dismiss_dialog()

    # 11. Nome já usado por outra conta
    type_into("edtEmail", "outra@churrasco.test")
    type_into("edtPassword", PASSWORD)
    tap(find(rid="btnCreateAccount"))
    wait_for(rid="btnCheckVerified")
    verify_email("outra@churrasco.test")
    tap(find(rid="btnCheckVerified"))
    wait_for(rid="btnSaveProfile")
    type_into("edtUserName", "Convidado")
    tap(find(rid="btnSaveProfile"))
    check("nome já reservado é recusado", wait_for(contains="reservado", timeout=20) is not None, screen_text()[:200])
    shot("09-nome-reservado")

    failed = [r for r in results if not r[1]]
    print(f"\n{len(results) - len(failed)}/{len(results)} passaram")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
