# Nano Augmentations (NeoForge 1.21.1)

Mod inspirado nos aprimoramentos de Deus Ex: Mankind Divided.

## Rodar
1. JDK 21.
2. Este projeto não inclui o Gradle Wrapper. Baixe o MDK em https://github.com/neoforged/MDK
   e copie `gradlew`, `gradlew.bat` e a pasta `gradle/` para cá (ou use um Gradle 8.x instalado).
3. Se a versão do NeoForge ou do plugin `net.neoforged.moddev` em `build.gradle` não for resolvida,
   troque pela mais recente da linha 21.1.x (veja no MDK).
4. `./gradlew runClient` para testar, `./gradlew build` para gerar o .jar em `build/libs`.

## Como jogar
- Kits Praxis: baús de estruturas (cidade antiga, bastião, fortaleza, End City, câmaras de provação,
  mansão, posto de pilhagem, vilas, templos...) e drops de Ravager, Evoker, Elder Guardian, Warden, Wither e Dragão.
  Também estão na aba criativa. Clique com o botão direito para ganhar 1 ponto Praxis.
- `U` abre a tela de aprimoramentos. Escolha a região do corpo, o aprimoramento e clique em INSTALAR.
- `O` Overclock: 15 s de potência x1.5 sem gasto de energia, depois 15 s de sobreaquecimento (energia zerada,
  aprimoramentos ativáveis offline). Exige 50+ de energia.
- Teclas padrão: C camuflagem, B visão inteligente, X hacking remoto, H Titan, G Typhoon, R Tesla, V Icarus Dash.
  Tudo é remapeável em Controles.

## Limitações conhecidas
- Visual das peças: só o seu próprio personagem mostra (outros jogadores não recebem seus dados de aug).
- Visão Inteligente usa o efeito Glowing, então o brilho é visível para todos.
- Se for publicar, troque nomes (Icarus, Titan, Typhoon, Tesla, CASIE...) e arte por identidade própria.
