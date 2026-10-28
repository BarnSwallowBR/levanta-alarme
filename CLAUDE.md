# Levanta! — app de alarme forte (Android)

App pessoal de alarme que só desliga com missão (matemática ou QR code) e fala frases
motivacionais em voz alta (TTS do celular). Kotlin + Jetpack Compose, minSdk 26, targetSdk 35.

## Papéis dos agentes
- **Claude Code constrói**: implementa funcionalidades e corrige bugs.
- **Codex revisa**: depois de cada mudança relevante, rode `scripts/codex-review.sh` (ou o
  subagente `revisor-codex`). O Codex só lê e reporta, nunca edita. Corrija o que ele achar
  antes de dar push. As regras de revisão estão em `AGENTS.md`.

## Estrutura
- `data/` — `Alarm` (modelo + JSON), `AlarmRepository` (alarmes e sonecas em SharedPreferences),
  `SettingsRepository` (frases, nomes `{nome}`/`{pessoa}`, código QR).
- `alarm/` — `AlarmScheduler` (AlarmManager.setAlarmClock), `AlarmReceiver` (dispara),
  `BootReceiver` (reagenda após reinício/atualização/mudança de hora), `AlarmRingingService`
  (serviço em primeiro plano que segura o toque), `AlarmVoice` (som + TTS + volume crescente).
- `ui/` — `MainActivity` (lista, edição, ajustes), `AlarmActivity` (tela sobre o bloqueio
  com as missões), `QrScanner` (CameraX + ML Kit). Tema "Amanhecer" em `Theme.kt`
  (`Dawn`, fonte Bricolage em `res/font`, `LevantaIcons`) e peças em `Components.kt`.
- Voz (ver `docs/voz.md`): por frase toca gravação do usuário > voz neural empacotada
  (`assets/voice/<voz>/<hash>.mp3`, gerada por `scripts/gerar-vozes.py`) > TTS do sistema.
  `data/VoiceClips` resolve o áudio pelo hash do texto renderizado; `data/VoiceRepository`
  guarda a voz escolhida. Mudou as frases padrão? Rode o script de novo.

## Regras que não podem quebrar
- O alarme só para por: missão cumprida (`ACTION_DISMISS`), soneca dentro do limite, ou o
  limite de segurança de 30 minutos. Botão voltar não desliga.
- Todo alarme salvo passa por `AlarmScheduler.save/delete` (nunca direto no repositório),
  senão o AlarmManager fica dessincronizado.
- Alarme repetido é reagendado no `AlarmReceiver` antes de tocar; alarme de uma vez é desligado.
- Textos da interface em português do Brasil.

## Agentes nesta máquina
O servidor tem 7 GB de RAM e já roda outros projetos: abrir mais de um Claude Code extra
causa OOM (o kernel mata processos, inclusive de outros apps). Rode um agente por vez;
o Codex (`scripts/codex-review.sh`) é leve e pode rodar junto.

## Build
Não há Android SDK nesta máquina. O APK é compilado pelo GitHub Actions
(`.github/workflows/build.yml`) a cada push na `main` e publicado em Releases.
A chave de assinatura fica nos secrets `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`; a cópia local
está em `keystore/` (fora do git). Sem a mesma chave o celular não aceita atualizar o app.
