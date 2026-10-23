"""Recuperação de conta antiga, do pedido no app até a aprovação pelo administrador.

Precisa do backend com CHURRASCO_LEGACY_ONBOARDING_ENABLED=1,
CHURRASCO_LEGACY_MIGRATION_WINDOW=1 e CHURRASCO_MIGRATION_REVIEWER_UID=reviewer-uid-e2e.
"""
import subprocess, sys, time
from drive import (ADB, APK, AUTH, API, FCM_SERVER, PASSWORD, PKG, PROJECT, adb, check, dismiss_dialog, find, http,
                   reset_state, results, scroll_to, shot, sign_in, tap, type_into, verify_email, wait_for)

REVIEWER_UID = "reviewer-uid-e2e"


def seed_legacy_user(name):
    """Perfil da versão sem login: só nome, sem firebaseUid."""
    subprocess.run(["node", "-e",
                    "require('mongoose').connect('mongodb://127.0.0.1:27027/churrasco_test_e2e?replicaSet=testset')"
                    f".then(m=>m.connection.db.collection('users').insertOne({{username:'{name}',displayName:'{name}',fcmTokens:[]}}))"
                    ".then(()=>process.exit(0))"], cwd=FCM_SERVER, check=True)


def create_reviewer(email):
    # Com o token "owner" o emulador aceita escolher o UID (igual ao configurado no servidor).
    req_body = {"localId": REVIEWER_UID, "email": email, "password": PASSWORD, "emailVerified": True}
    import json, urllib.request
    req = urllib.request.Request(f"{AUTH}/identitytoolkit.googleapis.com/v1/projects/{PROJECT}/accounts",
                                 data=json.dumps(req_body).encode(), method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", "Bearer owner")
    urllib.request.urlopen(req, timeout=15).read()
    status, _ = http("POST", f"{API}/users/register", {"username": "Admin", "displayName": "Admin"}, sign_in(email))
    return status


def sign_out_in_app():
    tap(scroll_to(rid="btnSwitchUser"))
    tap(wait_for(text="Sair"))
    wait_for(rid="btnSignIn")


def sign_in_in_app(email):
    type_into("edtEmail", email)
    type_into("edtPassword", PASSWORD)
    tap(find(rid="btnSignIn"))


def main():
    reset_state()
    seed_legacy_user("Antigo")
    check("administrador criado", create_reviewer("admin@churrasco.test") == 201)

    adb("uninstall", PKG, check=False)
    adb("install", "-r", "-g", APK)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")

    # 1. Dono da conta antiga cria acesso novo e pede o nome "Antigo"
    owner = "antigo@churrasco.test"
    wait_for(rid="btnCreateAccount")
    type_into("edtEmail", owner)
    type_into("edtPassword", PASSWORD)
    tap(find(rid="btnCreateAccount"))
    wait_for(rid="btnCheckVerified")
    verify_email(owner)
    tap(find(rid="btnCheckVerified"))
    wait_for(rid="btnSaveProfile")
    check("botão de recuperar conta antiga aparece", wait_for(rid="btnLegacyAccount", timeout=20) is not None)
    type_into("edtUserName", "Antigo")
    tap(find(rid="btnSaveProfile"))
    check("nome antigo não pode ser pego direto", wait_for(contains="reservado", timeout=20) is not None)
    dismiss_dialog()
    tap(find(rid="btnLegacyAccount"))
    tap(wait_for(text="Enviar pedido"))
    check("mostra pedido em análise", wait_for(contains="está com o administrador", timeout=20) is not None)
    shot("10-pedido-em-analise")
    tap(find(rid="btnRefreshStatus"))
    check("atualizar mantém em análise", wait_for(contains="está com o administrador", timeout=20) is not None)

    # 2. Administrador aprova pelo app
    tap(find(rid="btnUseAnotherAccount"))
    wait_for(rid="btnSignIn")
    sign_in_in_app("admin@churrasco.test")
    check("administrador entra", wait_for(contains="Oi, Admin") is not None)
    review = scroll_to(rid="btnReviewLegacyMain")
    check("botão de revisão aparece só para o administrador", review is not None)
    tap(review)
    check("pedido aparece na revisão", wait_for(contains=owner, timeout=20) is not None)
    shot("11-revisao")
    tap(wait_for(text="Confirmar titular e aprovar"))
    field = wait_for(contains="Como você confirmou")
    tap(field)
    adb("shell", "input", "text", "Conferido%spessoalmente")
    tap(wait_for(text="OK"))
    check("lista fica vazia depois de aprovar", wait_for(contains="Nenhuma solicitação pendente", timeout=30) is not None)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)

    # 3. Dono entra e recebe o nome antigo
    sign_out_in_app()
    sign_in_in_app(owner)
    check("dono entra com o nome antigo", wait_for(contains="Oi, Antigo", timeout=40) is not None)
    shot("12-conta-recuperada")
    check("botão de revisão não aparece para quem não é admin", find(rid="btnReviewLegacyMain") is None)

    failed = [r for r in results if not r[1]]
    print(f"\n{len(results) - len(failed)}/{len(results)} passaram")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
