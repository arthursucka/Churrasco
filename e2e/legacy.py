"""Recuperação de conta antiga, do pedido no app até a aprovação pela tela do administrador.

Precisa do backend com CHURRASCO_LEGACY_ONBOARDING_ENABLED=1,
CHURRASCO_LEGACY_MIGRATION_WINDOW=1 e CHURRASCO_MIGRATION_REVIEWER_UID=reviewer-uid-e2e.
"""
import time
from support import (adb, api_user, check, dismiss_dialog, find, finish, install_fresh, launch, mongo, reset_state,
                     scroll_to, screen_text, shot, tap, type_into, verify_email, wait_for, PKG, sao_paulo)


def sign_in_in_app(email):
    type_into("edtEmail", email)
    type_into("edtPassword", "teste-local-123")
    tap(scroll_to(rid="btnSignIn"))


def main():
    reset_state()
    # Perfil da v1: só o nome, sem firebaseUid. Um churrasco dele também fica pelo nome.
    date, hour = sao_paulo(60 * 24)
    mongo(".then(m=>m.connection.db.collection('users').insertOne({username:'Antigo',displayName:'Antigo',fcmTokens:[]}))")
    mongo(f".then(m=>m.connection.db.collection('churrascos').insertOne({{schemaVersion:2,churrascoDate:'{date}',hora:'{hour}',local:'Churrasco antigo',address:'',organizerUid:null,organizerLegacyUsername:'Antigo',participants:[],contributions:[],expenses:[],createdAt:new Date(),__v:0}}))")
    api_user("admin@churrasco.test", "Admin", uid="reviewer-uid-e2e")

    install_fresh()
    launch()

    # 1. Dono da conta antiga cria acesso novo e pede o nome "Antigo"
    owner = "antigo@churrasco.test"
    wait_for(rid="btnCreateAccount")
    type_into("edtEmail", owner)
    type_into("edtPassword", "teste-local-123")
    tap(scroll_to(rid="btnCreateAccount"))
    wait_for(rid="btnCheckVerified")
    verify_email(owner)
    tap(scroll_to(rid="btnCheckVerified"))
    wait_for(rid="btnSaveProfile")
    type_into("edtUserName", "Antigo")
    tap(scroll_to(rid="btnSaveProfile"))
    check("nome antigo não pode ser pego direto", wait_for(contains="conta antiga", timeout=30) is not None, screen_text()[:200])
    dismiss_dialog()
    check("botão de recuperar conta antiga aparece", scroll_to(rid="btnLegacyAccount", timeout=20) is not None)
    tap(scroll_to(rid="btnLegacyAccount"))
    tap(wait_for(text="Enviar pedido"))
    check("mostra pedido em análise", wait_for(contains="está com o administrador", timeout=30) is not None)
    shot("10-pedido-em-analise")

    # 2. Administrador aprova pelo app
    tap(scroll_to(rid="btnUseAnotherAccount"))
    wait_for(rid="btnSignIn", timeout=30)
    sign_in_in_app("admin@churrasco.test")
    check("administrador entra", wait_for(contains="Oi, Admin", timeout=60) is not None)
    review = scroll_to(rid="btnReviewLegacyMain", timeout=30)
    check("botão de revisão aparece só para o administrador", review is not None)
    tap(review)
    check("pedido aparece na revisão", wait_for(contains=owner, timeout=30) is not None)
    shot("11-revisao")
    tap(scroll_to(text="Confirmar titular e aprovar"))
    time.sleep(1)
    edit = next((n for n in __import__("support").nodes(__import__("support").dump()) if n.get("class", "").endswith("EditText")), None)
    tap(edit)
    adb("shell", "input", "text", "Conferido%spessoalmente")
    tap(wait_for(text="OK"))
    check("lista fica vazia depois de aprovar", wait_for(contains="Nenhuma solicitação pendente", timeout=40) is not None)

    # 3. Dono entra com o nome antigo e vê o churrasco dele
    adb("shell", "am", "start", "-n", f"{PKG}/.SplashActivity")
    tap(wait_for(rid="nav_profile", timeout=40))
    tap(scroll_to(rid="btnSwitchUser"))
    tap(wait_for(text="Sair"))
    wait_for(rid="btnSignIn", timeout=30)
    sign_in_in_app(owner)
    check("dono entra com o nome antigo", wait_for(contains="Oi, Antigo", timeout=60) is not None)
    check("revisão não aparece para quem não é admin", find(rid="btnReviewLegacyMain") is None)
    tap(wait_for(rid="nav_events", timeout=30))
    check("churrasco antigo volta para o dono", wait_for(contains="Churrasco antigo", timeout=40) is not None)
    shot("12-conta-recuperada")

    finish()


if __name__ == "__main__":
    main()
