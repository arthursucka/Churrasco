"""Teste de ponta a ponta do Churrasco 2.0 (build localTest) num emulador Android.

Precisa de: emuladores do Firebase (auth 9099, database 9000, projeto demo-churrasco),
MongoDB de teste (npm run mongo), backend em CHURRASCO_LOCAL_TEST=1 na porta 3000 e um
emulador Android ligado. Ver e2e/README.md.
"""
import time
from support import (API, PKG, adb, api_user, befriend, check, db_get, dismiss_dialog, event, find, fill,
                     finish, http, install_fresh, launch, nodes, dump, reset_state, sao_paulo, scroll_to,
                     screen_text, shot, sign_in, tap, tap_text, type_into, verify_email, wait_for)


def field(hint, timeout=20):
    """Campo de texto pelo hint (os campos montados em código não têm id)."""
    end = time.time() + timeout
    while time.time() < end:
        for n in nodes(dump()):
            if n.get("class", "").endswith("EditText") and hint in (n.get("hint") or ""):
                return n
        node = scroll_to(contains=hint, timeout=4)
        if node is not None:
            return node
    return None


def sign_up_in_app(email, name):
    type_into("edtEmail", email)
    type_into("edtPassword", "teste-local-123")
    tap(scroll_to(rid="btnCreateAccount"))
    wait_for(rid="btnCheckVerified")
    verify_email(email)
    tap(scroll_to(rid="btnCheckVerified"))
    wait_for(rid="btnSaveProfile")
    type_into("edtUserName", name)
    tap(scroll_to(rid="btnSaveProfile"))
    return wait_for(contains=f"Oi, {name}", timeout=60) is not None


def sign_in_in_app(email, password="teste-local-123"):
    type_into("edtEmail", email)
    type_into("edtPassword", password)
    tap(scroll_to(rid="btnSignIn"))


def sign_out_in_app():
    adb("shell", "am", "start", "-n", f"{PKG}/.SplashActivity")
    wait_for(rid="btnSwitchUser", timeout=30)
    tap(scroll_to(rid="btnSwitchUser"))
    tap(wait_for(text="Sair"))
    wait_for(rid="btnSignIn", timeout=30)


def open_active_event(place):
    adb("shell", "am", "start", "-n", f"{PKG}/.SplashActivity")
    tap(wait_for(rid="btnActiveChurrascos", timeout=40))
    tap(wait_for(contains=place, timeout=40))
    return wait_for(contains="Churrasco de", timeout=40) is not None


def main():
    org_email, g1_email, g2_email, g3_email = (f"{n}@churrasco.test" for n in ("organizador", "gui", "gabi", "novo"))
    reset_state()
    install_fresh()
    launch()

    # 1. Conta nova pelo app, com confirmação de e-mail
    check("tela de login aparece", wait_for(rid="btnCreateAccount") is not None)
    check("botão do Google aparece", find(rid="btnGoogle") is not None)
    check("cadastro com e-mail confirmado chega na tela principal", sign_up_in_app(org_email, "Organizador"))
    shot("01-principal")
    org_token, org_uid = sign_in(org_email)

    # 2. Amigos e churrasco preparados pela API
    g1_token, g1_uid = api_user(g1_email, "Gui")
    g2_token, g2_uid = api_user(g2_email, "Gabi")
    g3_token, g3_uid = api_user(g3_email, "Novo")
    befriend(org_token, org_email, g1_token, g1_email)
    befriend(org_token, org_email, g2_token, g2_email)
    http("PATCH", f"{API}/users/me", {"pixKey": "gui@pix.com"}, g1_token)
    date, hour = sao_paulo(30)
    status, body = http("POST", f"{API}/churrascos", {
        "churrascoDate": date, "hora": hour, "local": "Casa do teste", "address": "Av. Paulista, 1000, São Paulo",
        "invitedUids": [g1_uid, g2_uid]}, org_token)
    check("churrasco criado pela API", status == 201, str(body))
    eid = body["id"]
    picanha = {"category": "Carne bovina", "details": {"corte": "Picanha", "quantidade": "1,5 kg"}}
    http("POST", f"{API}/churrascos/{eid}/rsvp", {"status": "confirmed", "contributions": [picanha]}, g1_token)
    http("POST", f"{API}/churrascos/{eid}/rsvp", {"status": "confirmed", "contributions": [
        {"category": "Carne bovina", "details": {"corte": "Fraldinha", "quantidade": "2 kg"}},
        {"category": "outro", "note": "Farofa da vó"}]}, g2_token)
    http("POST", f"{API}/churrascos/{eid}/messages", {"text": "Levo o gelo!"}, g1_token)
    http("POST", f"{API}/churrascos/{eid}/expenses", {"amountCents": 30000, "description": "Carnes"}, g1_token)
    http("POST", f"{API}/churrascos/{eid}/expenses", {"amountCents": 6000, "description": "Bebidas"}, g2_token)

    # 3. Tela do churrasco
    check("abre o churrasco pela lista de ativos", open_active_event("Casa do teste"))
    check("botões de Maps e Waze", find(contains="Maps") is not None and find(contains="Waze") is not None)
    shot("02-detalhes")
    check("duas pessoas levam carne bovina", scroll_to(contains="Picanha") is not None and scroll_to(contains="Fraldinha") is not None)
    check("item \"Outro\" aparece com o texto livre", scroll_to(contains="Farofa da vó") is not None)
    shot("03-itens")

    # 4. Contas e Pix: total 360, 120 para cada; o organizador deve 120 ao Gui
    check("mostra quanto o organizador deve", scroll_to(contains="Você manda R$ 120,00 para Gui") is not None, screen_text()[:300])
    tap_text("Pagar com Pix")
    check("Pix mostra a chave de quem recebe", wait_for(contains="Chave: gui@pix.com", timeout=15) is not None)
    shot("04-pix")
    tap(wait_for(text="Copiar código"))
    time.sleep(3)  # diálogo fechando e aviso de "copiado"

    # 5. Itens do organizador pela tela de itens
    tap_text("O que eu já garanto")
    check("tela de itens abre", wait_for(contains="O que você vai levar?", timeout=30) is not None)
    tap(scroll_to(contains="Carvão"))
    tap(scroll_to(text="5 kg"))
    shot("05-escolher-itens")
    tap(wait_for(text="Salvar meus itens"))
    time.sleep(3)
    mine = [c for c in event(eid, org_token)["contributions"] if c["uid"] == org_uid]
    check("itens do organizador salvos com quantidade", any(c["category"] == "Carvão" and c["details"].get("quantidade") == "5 kg" for c in mine), str(mine))

    # 6. Despesa pelo app
    tap_text("Lançar despesa")
    fill(field("O que foi"), "Carvao")
    fill(field("Valor"), "45,00")
    tap(wait_for(text="Salvar"))
    time.sleep(3)
    expenses = event(eid, org_token)["expenses"]
    check("despesa lançada pelo app", any(x["uid"] == org_uid and x["amountCents"] == 4500 for x in expenses), str(expenses))

    # 7. Chat (regras do banco exigem login e participação)
    check("chat mostra a mensagem do convidado", scroll_to(contains="Levo o gelo!") is not None)
    fill(field("Digite uma mensagem"), "Fechado, ate mais tarde")
    tap(scroll_to(text="Enviar"))
    time.sleep(3)
    messages = list((db_get(f"churrascos/{eid}/messages") or {}).values())
    check("mensagem do app chega com o UID", any(m.get("senderUid") == org_uid and m["text"] == "Fechado, ate mais tarde" for m in messages), str(messages))

    # 8. Localização continua com a tela bloqueada
    adb("emu", "geo", "fix", "-46.6333", "-23.5505", check=False)
    tap(scroll_to(text="📍 Estou a caminho"))
    time.sleep(8)
    first = (db_get(f"churrascos/{eid}/locations") or {}).get(org_uid, {})
    check("localização enviada pelo serviço", "latitude" in first, str(first))
    check("aviso de compartilhamento em andamento", scroll_to(contains="mesmo com a tela bloqueada", timeout=10) is not None)
    shot("06-mapa")
    adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
    time.sleep(2)
    adb("emu", "geo", "fix", "-46.6500", "-23.5600", check=False)
    moved = {}
    for _ in range(12):
        time.sleep(5)
        moved = (db_get(f"churrascos/{eid}/locations") or {}).get(org_uid, {})
        if abs(moved.get("latitude", 0) - (-23.56)) < 0.001:
            break
    check("localização atualiza com a tela bloqueada", abs(moved.get("latitude", 0) - (-23.56)) < 0.001, str(moved))
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard", check=False)
    time.sleep(2)
    tap(scroll_to(text="Parar de compartilhar"))
    time.sleep(3)
    check("parar remove a localização do servidor", not (db_get(f"churrascos/{eid}/locations") or {}).get(org_uid))

    # 9. Edição pelo organizador
    tap_text("Editar data, local ou convidados")
    wait_for(contains="Editar churrasco", timeout=30)
    time.sleep(2)  # o formulário carrega os dados do churrasco
    fill(field("Local"), "Sitio do teste")
    shot("07a-editar")
    tap(wait_for(text="Salvar"))
    time.sleep(3)
    check("edição salva o novo local", event(eid, org_token)["local"] == "Sitio do teste", screen_text()[:300])

    # 10. Link de convite
    tap_text("📤 Convidar com link")
    time.sleep(3)
    focus = adb("shell", "dumpsys", "window", "displays")
    check("abre a lista de apps para compartilhar", "intentresolver" in focus or "ChooserActivity" in focus or "ResolverActivity" in focus)
    shot("07-compartilhar")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    link = http("POST", f"{API}/churrascos/{eid}/invite-link", {}, org_token)[1]["payload"]

    # 11. Lembretes: um churrasco amanhã ganha avisos de 24 h e 2 h
    date2, hour2 = sao_paulo(60 * 30)
    http("POST", f"{API}/churrascos", {"churrascoDate": date2, "hora": hour2, "local": "Amanha", "address": ""}, org_token)
    adb("shell", "am", "start", "-n", f"{PKG}/.SplashActivity")
    tap(wait_for(rid="btnActiveChurrascos", timeout=40))
    wait_for(contains="Amanha", timeout=30)
    time.sleep(2)
    alarms = adb("shell", "dumpsys", "alarm")
    check("lembretes agendados", alarms.count("com.longynus.churrasco.REMINDER") >= 2 or alarms.count(PKG) >= 2)

    # 12. Outra pessoa entra pelo link e escolhe "Outro"
    sign_out_in_app()
    sign_in_in_app(g3_email)
    check("Novo entra com e-mail e senha", wait_for(contains="Oi, Novo", timeout=60) is not None)
    adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-p", PKG, "-d", link["url"])
    check("link abre o convite no app", wait_for(contains="Você foi convidado!", timeout=40) is not None, screen_text()[:300])
    shot("08-convite")
    tap_text("Vou! Escolher o que levar")
    tap(scroll_to(contains="Outro"))
    fill(field("O que você vai levar?"), "Gelo de coco")
    tap(wait_for(text="Confirmar presença"))
    check("presença confirmada", wait_for(contains="Você vai!", timeout=40) is not None)
    novo = event(eid, g3_token)
    check("contribuição \"Outro\" salva", any(c["uid"] == g3_uid and c["category"] == "outro" and c["note"] == "Gelo de coco" for c in novo["contributions"]))

    # 13. Pedido de amizade pela tela de amigos
    adb("shell", "am", "start", "-n", f"{PKG}/.SplashActivity")
    tap(scroll_to(contains="Amigos"))
    fill(field("E-mail"), org_email)
    tap(wait_for(text="Mandar pedido"))
    time.sleep(3)
    incoming = http("GET", f"{API}/friends", token=org_token)[1]["payload"]["incoming"]
    check("pedido de amizade chega ao organizador", any(f["uid"] == g3_uid for f in incoming), str(incoming))
    shot("09-amigos")

    # 14. Senha errada
    sign_out_in_app()
    sign_in_in_app(org_email, "senha-errada")
    check("senha errada mostra mensagem clara", wait_for(contains="incorretos", timeout=20) is not None)
    dismiss_dialog()

    finish()


if __name__ == "__main__":
    main()
