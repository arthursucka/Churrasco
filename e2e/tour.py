"""Tour de capturas para revisão de design: monta dados realistas e fotografa cada tela,
no tema claro e no escuro. Não verifica nada; use drive.py para isso."""
import os
import subprocess
import sys
import time
from support import (ADB, API, FCM_SERVER, MONGO_URI, PKG, SHOTS, adb, api_user, befriend, dump, find, http,
                     install_fresh, launch, nodes, reset_state, sao_paulo, scroll_to, tap, type_into, wait_for)

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(SHOTS, "tour")


def snap(name):
    os.makedirs(OUT, exist_ok=True)
    time.sleep(1.2)
    with open(os.path.join(OUT, f"{name}.png"), "wb") as f:
        f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)
    print("shot", name, flush=True)


def to_top():
    for _ in range(6):
        adb("shell", "input", "swipe", "30", "600", "30", "2100", "120")
    time.sleep(0.8)


def page_down():
    adb("shell", "input", "swipe", "30", "1900", "30", "700", "900")
    time.sleep(1)


def open_home():
    adb("shell", "am", "start", "-n", f"{PKG}/.SplashActivity")
    wait_for(rid="nav_home", timeout=40)
    wait_for(contains="Oi, Arthur", timeout=40)
    time.sleep(1.5)


def seed():
    reset_state()
    users = {}
    for key, email, name in [
        ("arthur", "arthur@churrasco.test", "Arthur"), ("gui", "gui@churrasco.test", "Guilherme"),
        ("gabi", "gabi@churrasco.test", "Gabriela"), ("cassi", "cassi@churrasco.test", "Cassiano"),
        ("lia", "lia@churrasco.test", "Lia"), ("davi", "davi@churrasco.test", "Davi")]:
        users[key] = (*api_user(email, name), email)
    t = lambda k: users[k][0]
    u = lambda k: users[k][1]
    for other in ("gui", "gabi", "cassi", "lia", "davi"):
        befriend(t("arthur"), users["arthur"][2], t(other), users[other][2])
    http("POST", f"{API}/friends", {"email": "novo.amigo@churrasco.test"}, t("arthur"))  # e-mail sem conta (ignorado)
    http("PATCH", f"{API}/users/me", {"pixKey": "gui@pix.com"}, t("gui"))
    http("PATCH", f"{API}/users/me", {"pixKey": "arthur@pix.com"}, t("arthur"))

    date, hour = sao_paulo(40)
    _, body = http("POST", f"{API}/churrascos", {
        "churrascoDate": date, "hora": hour, "local": "Casa do Arthur", "address": "Rua Augusta, 1500, São Paulo",
        "invitedUids": [u("gui"), u("gabi"), u("cassi"), u("lia"), u("davi")],
        "contributions": [{"category": "Carvão", "details": {"quantidade": "5 kg"}},
                          {"category": "Carne bovina", "details": {"corte": "Picanha", "quantidade": "2 kg", "tempero": "Só sal grosso"}}]},
        t("arthur"))
    eid = body["id"]
    http("POST", f"{API}/churrascos/{eid}/rsvp", {"status": "confirmed", "contributions": [
        {"category": "Carne bovina", "details": {"corte": "Fraldinha", "quantidade": "1,5 kg", "tempero": "Temperada"}},
        {"category": "Cerveja", "details": {"marca": "Heineken", "embalagem": "Long neck", "quantidade": "24 unidades"}}]}, t("gui"))
    http("POST", f"{API}/churrascos/{eid}/rsvp", {"status": "confirmed", "contributions": [
        {"category": "Linguiça", "details": {"tipo": "Toscana", "quantidade": "1 kg"}},
        {"category": "outro", "note": "Farofa de banana da vó"}]}, t("gabi"))
    http("POST", f"{API}/churrascos/{eid}/rsvp", {"status": "confirmed", "contributions": [
        {"category": "Gelo", "details": {"quantidade": "2 sacos de 5 kg"}},
        {"category": "Pão de alho", "details": {"sabor": "Com queijo", "quantidade": "2 pacotes"}}]}, t("cassi"))
    http("POST", f"{API}/churrascos/{eid}/rsvp", {"status": "declined"}, t("davi"))
    for who, amount, desc in [("arthur", 8990, "Picanha"), ("gui", 41500, "Fraldinha, cerveja e carvão extra"), ("cassi", 4590, "Gelo e pão de alho")]:
        http("POST", f"{API}/churrascos/{eid}/expenses", {"amountCents": amount, "description": desc}, t(who))
    for who, text in [("gui", "Levo a caixa de som também 🔊"), ("gabi", "Alguém tem espeto sobrando?"), ("arthur", "Tenho sim, pode vir sem"), ("cassi", "Chego umas 13h")]:
        http("POST", f"{API}/churrascos/{eid}/messages", {"text": text}, t(who))
        time.sleep(0.3)
    _, s = http("POST", f"{API}/churrascos/{eid}/location-session", {}, t("gui"))
    http("POST", f"{API}/churrascos/{eid}/location", {"latitude": -23.5558, "longitude": -46.6620, "sessionId": s["sessionId"]}, t("gui"))
    _, s = http("POST", f"{API}/churrascos/{eid}/location-session", {}, t("gabi"))
    http("POST", f"{API}/churrascos/{eid}/location", {"latitude": -23.5489, "longitude": -46.6388, "sessionId": s["sessionId"]}, t("gabi"))

    # Convite pendente para o Arthur (de outra pessoa) e um churrasco passado.
    d2, h2 = sao_paulo(60 * 24 * 6)
    http("POST", f"{API}/churrascos", {"churrascoDate": d2, "hora": h2, "local": "Sítio da Gabi", "address": "Estrada do Sol, km 3, Ibiúna",
                                       "invitedUids": [u("arthur")],
                                       "contributions": [{"category": "Carne suína", "details": {"corte": "Costelinha", "quantidade": "3 kg"}}]}, t("gabi"))
    d3, h3 = sao_paulo(-60 * 24 * 20)
    subprocess.run(["node", "-e", f"require('mongoose').connect('{MONGO_URI}').then(m=>m.connection.db.collection('churrascos').insertOne({{schemaVersion:2,churrascoDate:'{d3}',hora:'{h3}',local:'Aniversário do Cassi',address:'',organizerUid:'{u('arthur')}',participants:[{{uid:'{u('cassi')}',status:'confirmed'}}],contributions:[],expenses:[],createdAt:new Date(),__v:0}})).then(()=>process.exit(0))"], cwd=FCM_SERVER, check=True)
    return users, eid


def capture(theme):
    p = f"{theme}-"
    adb("shell", "cmd", "uimode", "night", "yes" if theme == "escuro" else "no")
    time.sleep(2)

    open_home(); snap(p + "03-inicio"); page_down(); snap(p + "03b-inicio-fim")
    tap(find(rid="nav_events")); wait_for(contains="Casa do Arthur", timeout=30); snap(p + "04-churrascos")
    tap(wait_for(text="Passados", timeout=10)); wait_for(contains="Aniversário", timeout=30); snap(p + "06-passados")
    tap(wait_for(text="Próximos", timeout=10)); tap(wait_for(contains="Sítio da Gabi", timeout=30))
    wait_for(contains="Você foi convidado", timeout=30); snap(p + "07-convite-detalhe")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")

    open_home(); tap(find(rid="nav_events")); tap(wait_for(contains="Casa do Arthur", timeout=30))
    wait_for(contains="Churrasco de", timeout=40); time.sleep(3)
    for i, name in enumerate(["Resumo", "Itens", "Contas", "Chat", "Mapa"]):
        tap(wait_for(text=name, timeout=10)); time.sleep(2.5)
        snap(p + f"08-aba-{i}-{name.lower()}")
        if name in ("Resumo", "Itens"):
            page_down(); snap(p + f"08-aba-{i}-{name.lower()}-fim"); to_top()

    if theme == "claro":
        tap(wait_for(text="Contas", timeout=10)); time.sleep(2)
        pix = scroll_to(text="Pagar com Pix", timeout=60)
        if pix is not None:
            tap(pix); wait_for(contains="Chave:", timeout=15); snap(p + "09-pix")
            tap(wait_for(text="Fechar", timeout=10))
        tap(scroll_to(text="Lançar despesa")); wait_for(text="Salvar", timeout=15); snap(p + "10-despesa")
        tap(wait_for(text="Cancelar", timeout=10))
        time.sleep(1)

    tap(wait_for(text="Resumo", timeout=10)); to_top(); tap(scroll_to(text="O que eu já garanto")); wait_for(contains="O que você vai levar?", timeout=30)
    snap(p + "11-itens-0"); page_down(); snap(p + "11-itens-1"); page_down(); snap(p + "11-itens-2"); page_down(); snap(p + "11-itens-3")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")

    open_home(); tap(wait_for(rid="btnCreateChurrasco")); wait_for(contains="Novo churrasco", timeout=30)
    snap(p + "12-criar-0"); page_down(); snap(p + "12-criar-1")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    open_home(); tap(wait_for(rid="nav_friends")); wait_for(contains="Seus amigos", timeout=30); snap(p + "13-amigos"); page_down(); snap(p + "13b-amigos")
    tap(wait_for(rid="nav_profile")); wait_for(contains="Chave Pix", timeout=30); snap(p + "14-perfil")


def main():
    seed()
    install_fresh()
    adb("shell", "cmd", "uimode", "night", "no")
    launch(); time.sleep(0.6); snap("claro-01-splash")
    wait_for(rid="btnCreateAccount", timeout=40); snap("claro-02-login")
    adb("shell", "cmd", "uimode", "night", "yes"); time.sleep(2); snap("escuro-02-login")
    adb("shell", "cmd", "uimode", "night", "no"); time.sleep(2)
    type_into("edtEmail", "arthur@churrasco.test"); type_into("edtPassword", "teste-local-123")
    tap(scroll_to(rid="btnSignIn")); wait_for(contains="Oi, Arthur", timeout=60)
    for theme in ("claro", "escuro"):
        capture(theme)
    adb("shell", "cmd", "uimode", "night", "no")
    print("fim")


if __name__ == "__main__":
    main()
