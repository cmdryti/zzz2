# DanceBot 2.0 (APK para rodar dentro do emulador)

Joga a Febre de Dança sozinho, **de dentro do Android do MuMu**: painel flutuante por cima do jogo
(▶/⏸, velocidade − / +, M = menus automáticos, – = minimizar em bolinha, ✕ = fechar) e fica rodando em
segundo plano. Mesma lógica do `dance_bot.py` (lê a barra de setas, toca nos 4 botões da direita, confere a tela antes
de cada toque, pausa sozinho quando os botões somem e navega Sair → Jogar Agora → Confirmar → Ir).

## 1. Gerar o APK (sem instalar nada no PC)

1. Crie um repositório novo e privado no GitHub e envie esta pasta inteira (pode arrastar os arquivos no site).
2. Abra a aba **Actions** → **Gerar APK** → **Run workflow**. Leva uns 3-5 minutos.
3. Entre na execução que terminou e baixe **DanceBot-apk** (dentro vem o `app-debug.apk`).

Alternativa: abra esta pasta no **Android Studio** e use Build → Build APK(s).

## 2. Instalar no MuMu

Arraste o `app-debug.apk` para a janela do MuMu (ou use o botão de instalar APK da barra lateral).

## 3. Preparar (uma vez só)

Abra o app **DanceBot** dentro do emulador:

1. **Permitir painel sobre o jogo** (sobreposição de tela).
2. **Ativar o serviço de Acessibilidade** → abra "DanceBot" na lista e ligue (é ele que faz os toques).
3. **Iniciar o robô** → aceite o pedido de gravação/captura de tela. O app volta para o jogo e o painel aparece.

## Uso

- ▶/⏸ liga/pausa tudo. `+` deixa mais rápido, `−` mais devagar (passo de 0,01 s; padrão 0,12 s).
- `M` liga/desliga os menus automáticos (azul = ligado).
- `–` vira uma bolinha; toque nela para abrir de novo. Arraste pela barra ⠿ (ou pela bolinha) para mudar de lugar.
- Para parar de vez: ✕ no painel, ou "Parar" na notificação.

## Limites conhecidos (leia)

- O robô só enxerga o que está na tela do Android. Se você sair do jogo para outro app/home **dentro do emulador**,
  ele fica esperando (não dá para tocar num jogo que não está na tela) e volta sozinho quando o jogo reaparecer.
  Minimizar a *janela do MuMu no Windows* é diferente: o Android continua rodando e o robô junto (confirme no seu MuMu,
  alguns modos reduzem a renderização de janelas minimizadas).
- O painel aparece na captura. Ele fica no canto superior esquerdo para não cobrir a barra de setas nem os botões;
  não arraste por cima deles.
- Se o jogo bloquear captura de tela, o robô vê tela preta e fica parado em "Aguardando imagem...".
- Feito para tela horizontal (landscape).
