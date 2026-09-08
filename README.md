# IcarusRPG

Plugin de RPG para servidores Paper/Spigot (`dev.icaro.foodtooltips.FoodTooltipsPlugin`,
registrado como `IcarusRPG` no `plugin.yml`).

> **Renomeado de NexusRPG pra IcarusRPG na v0.39.0.** O pacote Java
> (`dev.icaro.foodtooltips`) não mudou, mas o `name:` do `plugin.yml` mudou —
> e é esse campo que o Bukkit usa pra nomear a pasta de dados do plugin. Ao
> atualizar um servidor existente, mova `plugins/NexusRPG/` pra
> `plugins/IcarusRPG/` (config, dados de jogadores etc.) antes de subir o jar
> novo, ou o plugin sobe com uma pasta vazia e perde as configurações salvas.

## Estado deste repositório

Este repositório estava vazio; o único artefato disponível era o `.jar` compilado
da versão `0.21.0` (sem código-fonte). O código em `src/main/java` foi
**reconstruído por descompilação** (CFR) desse jar e reorganizado como projeto
Maven. Funcionalmente deve corresponder ao jar original, mas:

- Comentários originais e nomes de variáveis locais foram perdidos (o
  descompilador gera nomes genéricos em alguns trechos).
- Ainda não foi possível compilar dentro deste ambiente porque o repositório
  Maven da PaperMC (`repo.papermc.io`) está bloqueado pela política de rede
  desta sandbox. Compile localmente ou em CI com acesso normal à internet.
- Vale revisar o código reconstruído com calma antes de considerá-lo
  equivalente linha a linha ao original do Codex.

## Build

```
mvn package
```

Gera `target/IcarusRPG-0.43.0.jar`. Requer acesso ao repositório da PaperMC
(`https://repo.papermc.io/repository/maven-public/`) e, para o hook de
WorldGuard, ao repositório da EngineHub (`https://maven.enginehub.org/repo/`).

**Versionamento**: a cada mudança publicada, suba o número da versão em
`pom.xml` (`<version>`) e `src/main/resources/plugin.yml` (`version:`) —
os dois precisam bater. Updates menores só mexem no terceiro número (ex.:
`0.26.1` → `0.26.2`) — correções, ajustes de config, pequenos retoques de
texto/UI. Updates maiores (features novas, mudanças de sistema, como a
árvore de combate) sobem o segundo número e **zeram** o terceiro (ex.:
`0.26.2` → `0.27.0`, nunca `0.26.3`).

## Módulos

- `bestiary` — catálogo de mobs e marcos (milestones) de progresso.
- `builder` — Varinha do Construtor (estende um bloco em linha/coluna).
- `combat` — listener de combate e visuais de mob (labels/HP acima da cabeça).
- `destroyer` — Mão do Destruidor (limpa um bloco em linha/coluna, espelho da Varinha).
- `economy` — saldo de moedas dos jogadores.
- `food` — tooltips de comida.
- `global` — nível global, XP, cores de badge/tema.
- `i18n` — idiomas.
- `item` — sistema de raridade por Tiers (`ItemTierService`) e dano de espadas por material (`SwordDamageService`).
- `mining` — baú do tesouro, gemas, menu de mineração.
- `protect` — hooks de proteção (WorldGuard / GriefPrevention).
- `skills` — habilidades de combate, skills gerais.
- `stats` — status do jogador e HUD.

O pacote `shop` (loja, itens, portais) foi removido — ver "Loja removida
(por enquanto)" mais abaixo.

## Árvore de Habilidades de Combate

As 15 habilidades de combate originais restantes (a 16ª, `TELEKINESIS`, saiu da
árvore — ver abaixo) deixaram de ser liberadas automaticamente por nível. Agora
elas (mais 4 novas: `RUTHLESS_STRIKES` e `UNDYING_WILL` passivas, `ARCANE_SLASH`
e `VITAL_TOUCH` ativas) vivem em uma árvore de 19 nós (`CombatTreeNode`),
organizada em 3 ramos temáticos (Fúria, Sangue, Precisão) que convergem em nós
de sinergia e no capstone `APEX_WARRIOR`.

**Telekinesis é universal**: em vez de fazer parte de uma árvore de skill
específica, `TELEKINESIS` agora é um perk liberado automaticamente para todo
jogador que atingir o **Nível Global** configurado (`global-level.telekinesis-level`,
padrão 3) — sem custo, sem depender de Combate ou Mineração. Uma vez liberado
(`GlobalLevelService#telekinesisUnlocked`), drops de abates hostis e de blocos
minerados vão direto pro inventário, e itens soltos próximos também são sugados
num raio configurável (`global-level.telekinesis-radius`, padrão 3 blocos). O
status aparece no menu "Seus status" (`/skills`).

- **Moeda**: **Pontos de Sangue** 🩸 (`CombatValorService`). Desbloquear e melhorar um
  nó é gated *somente* por Pontos de Sangue e pelo(s) nó(s) anterior(es) da árvore
  (rank ≥ 1) — não existe mais requisito de nível de Combate. Cada mob hostil
  dropa exatamente a quantia mostrada no seu card do Bestiário (`awardedCombatXp()`,
  arredondado); mobs fora do catálogo caem num fallback baseado em vida máxima.
  Subir de nível de Combate também dá um bônus fixo.
- **Ranks e custo por tier**: o rank máximo agora é puramente função do
  *tier* do nó (`CombatTreeNode`, campo `maxRank`) — tier 1 (raízes) vai até
  10, e cada tier seguinte sobe: 14/18/22/26, com o capstone `APEX_WARRIOR`
  (tier 6) no maior de todos, 32. Antes o rank máximo variava até dentro de
  um mesmo tier (10 a 15 lado a lado); agora todo nó no mesmo tier tem o
  mesmo teto, um progressão mais longa e mais previsível. O custo de cada
  rank escala tanto com o rank quanto com o tier (raiz = tier 1, calculado
  automaticamente a partir dos pré-requisitos em `CombatTreeNode`): `custo =
  (base + custo-por-tier·(tier-1)) + (custo-por-rank + custo-por-rank-por-tier·(tier-1))·(rank-1)`,
  configurável em `combat-tree.*` no `config.yml`. Nós mais profundos (ex.:
  `APEX_WARRIOR`, tier 6) custam bem mais por rank que os nós-raiz. As
  fórmulas de efeito (dano, cura, cooldown, etc.) interpolam linearmente do
  valor de rank 1 ao de rank máximo de cada habilidade (`CombatTreeMath#lerp`,
  recebendo `maxRank` como parâmetro explícito).
- **Nível de Combate mínimo por tier**: além de Blood Points e pré-requisitos,
  cada tier da árvore agora também exige um Nível de Combate mínimo pra
  desbloquear/melhorar um nó (`combat-tree.tier-level-requirements` no
  config.yml — padrão `[0, 15, 35, 60, 90, 130]` pros tiers 1-6;
  `CombatAbilityService#levelRequirement`, checado em `purchaseRank`). Isso
  volta um gate de nível que tinha sido removido numa leva anterior (então
  só Blood Points/pré-requisitos importavam) — dessa vez escalando por tier
  em vez de ser um valor único pra árvore toda, então subir de verdade no
  Combate também é necessário pra chegar no topo, não só farmar moeda. A
  tooltip de cada nó mostra o requisito (✔/✖) junto dos pré-requisitos.
- **Menu**: `/skills` → "Árvore de Combate" (`CombatTreeMenuService`). Clique
  esquerdo desbloqueia/melhora; shift-clique ativa/desativa passivas
  desbloqueadas; clique direito conjura `ARCANE_SLASH`/`VITAL_TOUCH`.
  Ícone por estado: carvão = bloqueada, esmeralda = desbloqueada, diamante
  = rank máximo; variante em bloco = habilidade ativa, variante em
  minério/gema = passiva. O botão de voltar fica no canto inferior esquerdo
  e a cabeça do jogador (moeda/legenda) no canto inferior direito. O
  preenchimento dos slots vazios é vidro preto (não carvão — nós bloqueados
  já usam esse ícone, então um filler de carvão os esconderia no fundo).
  **Layout inspirado em Heart of the Mountain/Heart of the Forest (Hypixel
  SkyBlock)**: em vez das 3 colunas retas de antes convergindo num só ponto,
  cada nó agora fica deslocado (esquerda/direita) em relação à coluna do seu
  pré-requisito — RUTHLESS_STRIKES/`VAMPIRISM`/`HUNTERS_INSTINCT` (as raízes)
  ficam nas mesmas colunas de sempre, mas os filhos delas zigzagueiam a
  partir daí, e VAMPIRISM sozinha se abre em 3 caminhos (`BLOOD_LUST`,
  `TREASURE_HUNTER`, `VITAL_TOUCH`). A silhueta ainda afunila conforme sobe
  (linha das raízes ocupa 5 colunas, o meio da árvore chega a ocupar 7-8,
  depois estreita de novo até `APEX_WARRIOR` sozinho no topo) — a mesma
  leitura de "montanha" de antes, só que agora são os próprios ícones das
  habilidades que desenham o formato, não o fundo. Só o `slot` de cada
  `register()` em `CombatTreeNode` mudou — ramos, pré-requisitos, custos e
  ranks continuam exatamente os mesmos.
  **Coluna 0 é um medidor de Nível de Combate**: como a coluna mais à
  esquerda ficou livre em toda linha (exceto a do botão de voltar), cada
  linha 0-4 ganha um vidro colorido mostrando o requisito de Nível de
  Combate daquele tier (`CombatTreeMenuService#placeLevelIndicators`) —
  verde se já alcançado, amarelo pro próximo nível que falta alcançar,
  vermelho pros mais distantes. O tier 1 (requisito 0, sempre cumprido) não
  tem vidro próprio, já que sua linha é onde fica o botão de voltar.
- **Tooltip detalhado**: cada nó mostra, além da descrição, uma leitura numérica
  "nível atual → próximo nível" de cada stat que ele concede
  (`CombatAbilityService#statPreview`), ex.: "Dano: 22.2% → 26.7%",
  "Recarga: 21.0s → 18.0s". Com a habilidade ainda bloqueada, mostra uma prévia
  do nível 1; já no nível máximo, mostra só o valor final.
- **Ordem de desbloqueio**: dentro de cada ramo, os nós estão ordenados para que
  o ganho no rank máximo nunca diminua conforme o tier sobe (ex.: no ramo Fúria,
  `ARMOR_PIERCER` agora vem antes de `BERSERKER`, já que davam a mesma coisa "fora
  de ordem" antes). O nó raiz de cada ramo agora é sempre uma passiva simples —
  `SWORD_THROW` (ativa) deixou de ser a raiz do ramo Precisão, com
  `HUNTERS_INSTINCT` em seu lugar.
- **Bestiário**: cada entrada mostra quantos Pontos de Sangue 🩸 aquele mob dropa
  (`BestiaryMenuService`), ao lado de moedas, XP de combate e drops.
- **Novas stats** (inspiradas em Hypixel SkyBlock, base configurável em
  `stats.*` no `config.yml`): Ferocity (chance de acerto extra em mobs),
  Swing Range (alcance de interação, quando o servidor expõe o atributo
  vanilla correspondente), Intelligence (Mana máxima + dano mágico),
  Ability Damage (multiplicador de dano mágico), Health Regen
  (regeneração natural), Vitality (novo recurso, separado de Mana/Vida,
  usado por `VITAL_TOUCH`) e Mending (multiplica cura aplicada a
  *outros* jogadores).
- **Toda stat de combate agora é upável pela árvore, exceto as 3 primeiras**
  (Vida, Defesa e Defesa Verdadeira ficam fora de propósito — vêm só de
  atributo vanilla/gear/config, sem fonte na árvore). As outras 8 ganham um
  bônus de uma habilidade específica, empilhado em cima da base do
  `config.yml` (`PlayerStatsService#stats`, ver o javadoc de
  `CombatAbilityService` pra lista completa nó → stat):
  `COMBAT_MASTERY` → Strength, `CLEAVE` → Ferocity (temático, já que Ferocity
  *é* chance de acerto extra e Cleave já acerta múltiplos alvos),
  `SWORD_THROW` → Swing Range, `ARCANE_SLASH` → Intelligence, `APEX_WARRIOR`
  → Ability Damage (o payoff mais amplo de fim de jogo, no capstone),
  `SOUL_HARVEST` → Health Regen, `UNDYING_WILL` → Vitalidade máxima,
  `SECOND_WIND` → Mending. Cada nó afetado mostra a linha extra no tooltip
  (`statPreview`) junto dos bônus que já tinha.
- **Resetar a árvore**: novo botão de TNT no menu (`CombatTreeMenuService`,
  ao lado do botão de voltar) — clique uma vez pra armar, clique de novo
  em até 10s pra confirmar. Zera o nível de toda habilidade e devolve
  **integralmente** os Pontos de Sangue gastos (mesma fórmula por tier de
  `nextRankCost`, somada por `CombatAbilityService#resetTree`), sem custo
  extra. Existe principalmente porque toda vez que uma leva desta rebalanceia
  fórmulas/ranks máximos, quem já tinha investido ficava preso na build
  antiga sem jeito de reorganizar.
- **Arremesso de Espada gira pra frente, não mais de lado**: o `ItemDisplay`
  usado no voo da espada rodava em torno do eixo Z (`Quaternionf#rotateZ`),
  o que parecia um giro "de disco" (plano, de lado). Trocado por
  `rotateX`, que faz a espada tombar pra frente (cambalhota) como um
  arremesso de faca de verdade (`SwordThrowListener`).
- **Arremesso de Espada corrigido no Bedrock**: `BedrockSwordThrowListener`
  detectava jogador de Bedrock só via `FloodgateApi` por reflexão — se o
  jar do Floodgate não estiver instalado *neste* servidor (por exemplo,
  Geyser só no proxy, ou um setup sem Floodgate) a detecção sempre falhava
  silenciosamente e o arremesso nunca disparava, mesmo com os controles
  certos. Agora tenta 3 sinais em cascata: (1) a assinatura de UUID que o
  Floodgate sempre gera (deriva o UUID só do XUID de 64 bits, zerando os
  64 bits superiores — funciona mesmo sem o jar do Floodgate no classpath,
  cobre o setup mais comum), (2) `FloodgateApi#isFloodgatePlayer` se o
  plugin Floodgate estiver instalado aqui, (3) `GeyserApi#connectionByUuid`
  se for o Geyser-Spigot (funciona mesmo sem Floodgate). Todos por reflexão
  (sem dependência de compilação), então nenhum deles precisa estar
  presente pro plugin compilar ou rodar.
- **Arremesso de Espada agora só atinge um inimigo por vez**: o dano do
  arremesso ia direto por `LivingEntity#damage()`, o que fazia o mesmo
  `CombatListener#damage` genérico das espadadas normais processar o hit —
  incluindo o respingo do Golpe em Arco (Cleave) pros inimigos próximos, se
  o jogador tivesse a passiva ativa. `CombatAbilityService#dealAbilityDamage`
  (mesmo mecanismo de flag que já protegia Corte Arcano) agora cobre o
  Arremesso de Espada também, sinalizando o hit pra `CombatListener` pular
  toda a pilha de multiplicadores de espadada — nível, crítico, Strength,
  Ferocity e o respingo do Cleave — então o arremesso aplica só a própria
  fórmula de dano (fração da arma) no único alvo que realmente acertou.
- **Números de dano flutuantes não são mais negrito**: o número de dano
  crítico (o arco-íris com ✦) usava `TextDecoration.BOLD` em cada caractere;
  removido (`MobVisualService#criticalNumber`). O número normal (não
  crítico) já não era negrito.

### Bugs pré-existentes corrigidos nesta mudança

Ao tocar nesses arquivos, dois problemas de descompilação que **não
compilariam** foram corrigidos (não relacionados ao pedido, mas bloqueavam o
build inteiro): `SwordThrowListener` e `BuriedTreasureService` tinham
`new BukkitRunnable(this){...}` — sintaxe inválida, já que `BukkitRunnable`
não tem construtor com argumento; o CFR decompilou de forma incorreta a
captura implícita da instância externa. Um terceiro, em
`SkillsMenuService.bagReward()`, tinha uma variável `slots` nunca atribuída
fora do caso `default` do switch — corrigido reescrevendo como switch
expression.

### Compilação

Esta sandbox não tem acesso ao repositório da PaperMC, então o build real
roda no GitHub Actions (`.github/workflows/build.yml`), disparado a cada
push — é lá que o `.jar` pronto pra baixar é gerado (aba **Actions** do
repositório → run mais recente → seção **Artifacts**). Foi assim que se
descobriu, entre outras coisas, que o Minecraft/Paper passou a usar
versionamento por data (`26.2`, exigindo JDK 25) e uma leva de bugs da
descompilação original que só o compilador real pegava.

Além disso, `CombatTreeMath` (a matemática da árvore — curva de custo,
Ferocity, fórmulas de escala por rank) é puro Java sem dependência do
Bukkit e roda com testes próprios (84 checks) direto nesta sandbox.

## Vida e Defesa: base de 100 HP, mobs 5× mais tanques, Defesa vem da armadura

Primeiro passo do "mob level scaling" planejado para depois que todas as
skills estiverem prontas:

- **HP padrão do jogador agora é 100** (`stats.base-health` no config.yml,
  antes o padrão vanilla de 20) — `PlayerStatsService#applyBaseHealth`, chamado
  no join. Os bônus que já existiam (milestones do Bestiário, HP por Nível
  Global) continuam somando em cima normalmente, sem mudança de comportamento
  ali.
- **Todo mob tem a Vida Máxima multiplicada por 5** (`mob-visuals.health-multiplier`
  no config.yml) — `CombatListener#scaleMobHealth`, aplicado uma vez por mob
  (guardado por uma flag na PDC do mob, então recarregar o plugin nunca
  multiplica de novo) tanto em spawns novos quanto nos mobs já existentes no
  mundo. Só a vida sobe — XP e Pontos de Sangue dropados por mobs sem entrada
  no Bestiário também sobem proporcionalmente (dependem da vida máxima), mas
  os valores fixos do Bestiário **não** mudam nesta leva.
- **Defesa agora vem só da armadura equipada — de jogadores e mobs — não
  mais do vanilla nem do nível de Mineração** (era um valor incorreto
  herdado da descompilação — `GeneralSkillService#defense` na verdade
  retornava o nível de Mineração). `ArmorDefenseService#defense` soma um
  valor fixo por peça/material a partir do `EntityEquipment` de qualquer
  `LivingEntity` (funciona igual pra jogador ou mob), e `neutralizeVanillaArmor`
  zera o atributo vanilla `ARMOR`/`ARMOR_TOUGHNESS` — em jogadores no join e a
  cada tick do HUD (mesmo padrão de "reaplicar sempre" já usado pra Swing
  Range/HP bônus — evita depender de qual pacote/versão o evento de troca de
  armadura do Paper usa), em mobs uma vez no spawn (`CombatListener#spawn`,
  mobs raramente trocam de equipamento depois de nascer). Só esse número
  conta pra redução de dano de qualquer `LivingEntity` (mesma curva de
  antes: `defesa / (defesa + 100)`, aplicada em `ArmorDefenseListener#defense`
  pra qualquer alvo, não só jogadores). Valores por peça (capacete/peitoral/calça/bota):

  | Material | Capacete | Peitoral | Calça | Bota | Total |
  |---|---|---|---|---|---|
  | Couro | 5 | 15 | 10 | 5 | 35 |
  | Cobre | 6 | 18 | 13 | 6 | 43 |
  | Corrente | 9 | 23 | 18 | 8 | 58 |
  | Ouro | 10 | 25 | 15 | 5 | 55 |
  | Ferro | 12 | 30 | 25 | 10 | 77 |
  | Diamante | 15 | 40 | 30 | 15 | 100 |
  | Netherite | 18 | 46 | 35 | 18 | 117 |

  Couro/Ferro/Ouro/Diamante foram os valores pedidos; Cobre, Corrente e
  Netherite foram escolhidos pra manter a mesma ordem relativa do vanilla
  (Couro < Cobre < Ouro ≲ Corrente < Ferro < Diamante < Netherite). Elmo de
  Tartaruga também ganha um valor pequeno (4) pra não virar defesa zero.
  Resistência a empurrão (perk do Netherite) não é mexida — só
  ARMOR/ARMOR_TOUGHNESS.

  **A tooltip do item mostra a Defesa de verdade**: `ArmorDefenseService#applyDefenseTooltip`
  reescreve a peça de armadura em si (equipada, solta no inventário, ou na
  mão secundária — em qualquer slot do inventário do jogador) pra esconder os
  atributos vanilla (`ItemFlag.HIDE_ATTRIBUTES`) e mostrar em vez disso uma
  linha "Defesa: +N" em verde, igual ao número que realmente conta. Roda no
  join e a cada tick do HUD, uma vez por item (guardado por uma flag na PDC
  do próprio item, então não sobrescreve encantos/renomes feitos depois).

  **Corrige Perfurador de Armadura no PvP e em mobs**: a habilidade checava
  se o alvo tinha `Attribute.ARMOR` vanilla > 0 pra decidir se dava o bônus
  de dano — como a Defesa vanilla é zerada de propósito, a habilidade nunca
  mais disparava contra outros jogadores (só contra os poucos mobs que já
  vinham com armadura vanilla). `CombatAbilityService#hasDefense` agora só
  checa `ArmorDefenseService#defense`, que funciona igual pra jogador ou
  mob.

## Nível Global

`/skills` → "Nível Global" (`SkillsMenuService#openGlobal`) abre uma tela no
mesmo formato paginado de 25-níveis-por-página das telas de Combate/skills
gerais, mostrando o que cada nível concede: +HP máximo (todo nível), +Strength
(a cada `global-level.levels-per-strength` níveis) e o desbloqueio da
Telecinese no nível configurado. Nível Global é baseado em XP linear e não tem
teto real, mas a tela só precisa ir até o nível 100 pra mostrar todo padrão de
recompensa pelo menos uma vez.

**Strength é um stat só**: o menu "Seus status" (`/skills`) mostrava
"Strength: 32" seguido de "Bônus do Nível Global: +32" — dois rótulos pro
mesmo número, já que Strength só vem do Nível Global (não existe fonte
adicional). Removida a linha duplicada (e o campo `globalStrength` redundante
em `PlayerStats`); agora é só "Strength: 32".

## Status & Equipamento

A cabeça no menu principal (`/skills`) foi renomeada para "Status &
Equipamento" e agora mostra, ao passar o mouse, só um resumo curto (estilo
Hypixel SkyBlock): Velocidade, Strength, Defesa, Dano Crítico, Chance
Crítica, Vida e Inteligência — cada um com seu próprio ícone (a Inteligência
ganhou o mesmo ícone ✎ já usado pela Mana, já que uma alimenta a outra).
"Velocidade" e "Dano Crítico" são novos na UI: Velocidade lê o atributo
vanilla `MOVEMENT_SPEED` do jogador convertido pra porcentagem (100 = andar
normal); Dano Crítico usa o novo `CombatAbilityService#criticalDamageMultiplier`
(config `combat.critical-damage-multiplier` ou o bônus de `CRITICAL_MASTERY`,
o que estiver ativo).

Clicar na cabeça abre uma tela nova ("Status & Equipamento",
`SkillsMenuService#openStats`) com o restante dos status, agrupados por ícone
temático (Combate/espada, Fortune de Mineração/Agricultura/Coleta com os
mesmos ícones do menu principal), e as 4 peças de armadura que o jogador tem
equipadas (capacete, peitoral, calças, botas) mostradas como os itens reais —
nome, encantos e tudo — lidas direto de `Player#getInventory()`; um slot
vazio mostra "Nada equipado."

**"Status de Combate" virou uma lista única e completa** (estilo Hypixel
SkyBlock): Vida, Defesa, Defesa Verdadeira, Strength, Chance Crítica, Dano
Crítico, Ferocity, Velocidade de Ataque, Alcance de Ataque, Inteligência,
Dano de Habilidade, Regen. de Vida, Vitality e Mending, tudo no mesmo item
(`combatStatsItem`, antes dividido em 4 itens separados —
Combate/Vitalidade/Magia/Defesa — que saíram do menu). Como quase todas
essas stats agora vêm parcialmente da árvore de combate, os números aqui já
refletem qualquer bônus de habilidade ativa.

**Velocidade de Ataque adicionada ao Status de Combate**: lia
`Attribute.ATTACK_SPEED` (mesmo padrão que já lia `MOVEMENT_SPEED` no
preview condensado), mas nunca tinha sido exibida em lugar nenhum apesar de
já ser uma mecânica real — soma da penalidade de espada do
`SwordDamageService` (-2.4) com o bônus por nível de Combate
(`CombatSkillService#attackSpeed`, curva configurável em
`combat.attack-speed-level-0/25/50`, que sobe de 4.0 em nível 0 pra 20.0 no
nível 50 e depois **para de crescer** até o 200, diferente de Chance/Dano
Crítico que continuam escalando linearmente até o teto).

## Reorganização do menu de Skills

O menu principal (`/skills`, `SkillsMenuService#openMain`) agora mostra
*só* a cabeça de status (slot 4) e os ícones de skill (Combate + as 6 gerais
+ Nível Global): Bestiário e Árvore de Combate deixaram de ter botão aqui —
só são acessíveis pela tela de Combate (`openCombat`, que já os tinha nos
slots 39/41); Bestiário continua alcançável também via `/bestiary`. Cores do
Nível e Loja continuam com botão no menu principal (mesmo já tendo comando
próprio, `/levelcolor` e `/shop`, o botão é mais rápido de achar que decorar
um comando). *(A Loja e o `/shop` foram removidos numa leva posterior — ver
"Loja removida (por enquanto)" mais abaixo; o botão dela some junto do menu
principal. A mecânica de Mochilas também foi removida numa leva ainda mais
posterior — ver "Mecânica de Mochilas removida" mais abaixo.)*

**Nível Global virou um ícone de skill**: em vez do botão separado que
tinha, agora fica no slot 13 (centralizado, logo abaixo da cabeça de status),
como se fosse mais uma skill. O ícone é uma cabeça customizada configurável
em `global-level.icon-texture` (cole o "Value" base64 de um custom head, por
exemplo do minecraft-heads.com); sem essa config, cai no ícone padrão (frasco
de experiência). A cabeça de overview dentro de "Status & Equipamento"
também foi enxugada — mostra só o Nível Global, sem Progresso/Nível de
Combate/Telecinese (essas informações já vivem na tela de Combate e na
própria tela de Nível Global).

**Tela de Nível Global mostra até o nível máximo real**: em vez de um limite
fixo arbitrário, `GlobalLevelService#maxAchievableLevel()` calcula o Nível
Global mais alto realmente alcançável — Combate e as 6 skills gerais todas no
nível 200, mais toda milestone de Bestiário e de Mineração reivindicada — e
usa esse número pra paginar a tela até lá.

## Sistema de raridade por Tiers (`dev.icaro.foodtooltips.item`)

Todo item do jogo agora tem uma raridade, expressa como **Tier** em vez das
palavras clássicas do Hypixel: `ItemTier` tem 6 valores, `S` > `A` > `B` > `C`
> `D` > `E`, cada um com a cor que a palavra Hypixel equivalente teria —
`S`=dourado (Legendary), `A`=roxo (Epic), `B`=azul (Rare), `C`=verde
(Uncommon), `D`=branco (Common) — mais um tier novo abaixo do Common, `E`,
cinza, pros blocos/itens mais "crus" e comuns do jogo (terra, pedregulho,
graveto, cascalho...). O rótulo mostrado no item é `TIER {letra}` (ex.:
`TIER C`) — mantido em inglês nas duas línguas, igual ao termo "Tier" que o
próprio pedido já usava em português.

**`ItemTierService#tierOf(Material)`** resolve o tier de qualquer `Material`:
primeiro checa um override de config (`item-tiers` no `config.yml`, vazio por
padrão), depois — se for uma ferramenta/arma/armadura — o tier vem da família
do material, comprimida de propósito pra deixar A e S livres pro equipamento
próprio do plugin (ver `SwordDamageService` pro rebalanceamento de dano que
acompanha essa mudança): Netherite=B, Diamond=C, e tudo de ferro pra baixo
(Iron/Golden/Copper/Stone/Wooden/Chainmail/Leather/Turtle) cai em D pelo
próprio `default` do switch — mais alguns casos sem prefixo como Arco/Tridente
julgados à parte —, depois um conjunto curado de itens notáveis (lingotes,
blocos de minério, drops raros) em S/A/B/C, depois um conjunto de "blocos
crus" em E (terra, pedra, cascalho, graveto...) — e cai em `D` (Common) como
padrão pra tudo que não foi listado, garantindo que **nenhum item fica sem
tier**. Nada disso precisa recompilar pra ajustar: qualquer `Material`
individual pode ser sobrescrito em `item-tiers:` no `config.yml`.

**Exibição no tooltip** segue o padrão das imagens de referência do Hypixel:
`ItemTierService#applyItemTiers(Player)` reescreve o lore de cada item do
inventário do jogador (armazenamento + armadura + offhand) uma única vez
(idempotente via flag na `PersistentDataContainer` do próprio `ItemMeta`,
mesmo padrão de `ArmorDefenseService#applyDefenseTooltip`) — pra ferramentas,
armas e armaduras, adiciona no fim do lore uma linha em negrito, cor do tier,
maiúscula, `TIER {letra} {TIPO}` (ex.: `TIER C PICKAXE`); pra qualquer outro
item (blocos, comida, ingredientes...) adiciona só o rótulo puro (`TIER D`),
sem sufixo — igual ao exemplo de "Dirt" mostrando só a raridade, sem mais
nenhuma linha. É aplicado no join do jogador (`ItemTierListener`) e
recarregado a cada tick do HUD (mesmo ciclo que já reaplica a tooltip de
Defesa), então cobre qualquer item que o jogador ganhe depois — compra na
loja, minério minerado, drop de mob, dado por comando — sem precisar
instrumentar cada sistema que entrega itens individualmente.

**Contorno da caixa do tooltip não é customizável sem resource pack**: no
vanilla, a moldura ao redor do tooltip é sempre a mesma textura fixa — só
muda de cor via o data component `tooltip_style`, que aponta pra uma sprite
definida num resource pack (o único estilo alternativo que o cliente já traz
pronto é o roxo "ominous" dos itens de Trial Chambers, não dá pra ter 6 cores
diferentes sem enviar texturas customizadas). Como alternativa sem essa
dependência, o próprio **nome do item** agora fica colorido e em negrito na
cor do tier (em vez de branco/padrão) — reaproveitando `Component.translatable`
já usado em `GemService`/`MiningMenuService` pra manter o nome original do
item quando ele não tem nome customizado.

**Bug corrigido: itens não empilhavam entre si.** A tooltip de tier só é
aplicada no tick seguinte a um item entrar no inventário (compra, minério
minerado, drop de mob...); nesse intervalo, o item novo ainda não tem a
tag "TIER X" enquanto uma pilha já existente do mesmo item já tem — o jogo
vê metadados diferentes e não junta as pilhas, e elas ficavam separadas
mesmo depois de as duas ganharem a mesma tag. `ItemTierService#applyItemTiers`
agora reagrupa as pilhas iguais do inventário (`coalesce`, respeitando o
stack size máximo) logo depois de aplicar a tag, todo tick — cura tanto essa
fragmentação causada pelo sistema de tiers quanto qualquer outra pilha
partida por acidente.

## Dano das espadas rebalanceado (`SwordDamageService`)

Os 4-8 de dano vanilla das espadas não tinham relação nenhuma com as
centenas de HP que essa RPG já usa (jogadores com 917 HP no exemplo do
Guardião do Núcleo) — trocado por uma progressão fixa por material:

| Material | Dano total |
|---|---|
| Madeira / Ouro | 20 |
| Pedra / Cobre | 25 |
| Ferro | 30 |
| Diamante | 35 |
| Netherite | 40 |

Essa progressão de dano é **independente** do tier de raridade (acima) — os
cinco materiais viram tiers diferentes de dano mas continuam todos Tier D
de raridade, só Diamante (C) e Netherite (B) saem do chão; dano e raridade
são dois eixos separados de propósito, do mesmo jeito que itens de mesma
raridade no Hypixel SkyBlock têm status bem diferentes entre si.

Implementado via `AttributeModifier` direto no `ItemMeta` (`Attribute.ATTACK_DAMAGE`,
`EquipmentSlotGroup.MAINHAND`), não como código-de-evento reescrevendo dano
na hora do hit — assim o Arremesso de Espada (que lê `Attribute.ATTACK_DAMAGE`
do jogador pra calcular sua fração de dano) automaticamente escala junto,
sem precisar de nenhum ajuste separado.

**Bug corrigido: a tooltip mostrava o valor configurado menos 1** (ex.:
Netherite configurado pra 40 aparecia como "+39 Attack Damage"). A causa: todo
jogador tem uma base vanilla de `Attribute.ATTACK_DAMAGE` = 1.0 sempre
presente, então um modificador de item de "39" somado a essa base dava 40 no
total — mas a *tooltip* do item mostra só o modificador do próprio item
(39), não o total já somado à base do jogador, então o número exibido nunca
batia com o configurado. `SwordDamageService#neutralizeBaseAttackDamage`
zera essa base vanilla uma vez por jogador (mesma ideia de
`ArmorDefenseService#neutralizeVanillaArmor` substituir Defesa por inteiro,
mas aqui cancelando só o 1.0 fixo da base — o que a própria espada contribui
continua intacto), então agora o modificador da espada é *o único*
componente que sobra e pode ser literalmente o valor configurado (40), sem
nenhuma subtração escondida em lugar nenhum.

**Efeito colateral, documentado de propósito**: como a base zerada é do
*jogador*, não da espada, ela vale sempre, independente do que estiver na
mão — então socar com a mão vazia agora causa 0 de dano em vez do 1 vanilla,
e ferramentas fora da progressão de espada (machado, tridente...) também
perdem esse 1 de base escondido, já que o próprio plugin não mexe nos
atributos delas.

**Pegadinha evitada**: setar explicitamente o `attribute_modifiers` de um
item **substitui** o conjunto implícito de modificadores que o material já
carregava (não soma em cima) — então toda espada perderia silenciosamente
sua penalidade de velocidade de ataque vanilla (a diferença entre os 4
ataques/s de mãos vazias e os 1.6 ataques/s característicos de espada) assim
que ganhasse o modificador de dano customizado. `SwordDamageService`
redeclara essa penalidade (`Attribute.ATTACK_SPEED`, -2.4) junto com o dano,
igual em todo material.

**Tooltip vanilla escondida, lore própria no lugar** (mesmo padrão de
`ArmorDefenseService#applyDefenseTooltip` pra Defesa): `ItemFlag.HIDE_ATTRIBUTES`
esconde o bloco "When in Main Hand" inteiro, substituído por duas linhas
próprias — "Dano de Ataque" (estático, o valor configurado, inserido uma
vez) e **"Velocidade de Ataque"**, que **não é estática**: mostra o valor
*real* que o jogador vai sentir empunhando aquela espada, recalculado a
cada refresh a partir do nível de Combate atual (`combat.attackSpeed(level)
+ (-2.4)`) — em vez do "-2.4" cru do modificador do item, que sozinho não
diz nada sobre a velocidade final (que varia de 1.6 no nível 0 até 17.6 no
nível 50+, ver a seção de Velocidade de Ataque acima). Por isso essa linha
específica não é idempotente feito o resto da tooltip: é conferida e
reescrita (só quando o texto realmente muda) toda vez que o ciclo
join+tick roda, pra nunca ficar mostrando um nível de Combate desatualizado.

## Loja removida (por enquanto)

Todo o pacote `dev.icaro.foodtooltips.shop` (`ShopService`, `ShopItem`,
`ShopMenuListener`, `ShopItemListener`, `PortalService`) foi removido, junto
com o comando `/shop`, o registro dos seus listeners e o botão "Loja" (slot
51) do menu principal de `/skills`. `ProtectionService` (usado só pelo
`ShopItemListener`) ficou pra trás sem uso — é inofensivo mantê-lo (não
depende de nada que foi removido), então não foi apagado, pra facilitar
reviver a loja depois se for o caso. `EconomyService` (moedas, `/coins`)
continua existindo normalmente — só perdeu o bônus de Caçador de Tesouros
(ver seção seguinte), não a loja em si.

## Árvore de Combate reduzida e reordenada

**12 habilidades removidas**: Vampirismo, Execução, Caçador de Tesouros,
Instinto do Caçador, Vontade Inabalável, Toque Vital, Maestria de Combate,
Corte Arcano, Golpe em Arco (Cleave), Perfurador de Armadura, Implacável e
Guerreiro Supremo. Sobraram 7: Golpes Implacáveis, Arremesso de Espada, Sede
de Sangue, Berserker, Colheita de Almas, Maestria Crítica e Segundo Fôlego —
cada bônus de stat que vinha de uma habilidade removida (Strength, Ferocity,
Inteligência, Dano de Habilidade, Vitalidade máxima) saiu de
`PlayerStatsService`/`EconomyService` junto com ela; o resto (Swing Range,
Regen. de Vida, Mending) continua vindo de Arremesso de Espada/Colheita de
Almas/Segundo Fôlego, que ficaram.

**Berserker agora ativa abaixo de 10% HP** (era 30%) — um bônus de
last-stand de verdade, não "a maior parte da luta com HP reduzido".

**Árvore reordenada**: Fúria e Sangue viraram cadeias de 3 níveis só
(Golpes Implacáveis → Berserker → Maestria Crítica; Sede de Sangue →
Colheita de Almas → Segundo Fôlego, cada uma agora raiz da própria cadeia
já que seus antigos pré-requisitos foram removidos). **Arremesso de Espada
subiu pro topo da árvore**: agora exige Maestria Crítica *e* Segundo Fôlego
(o topo das duas cadeias) em vez de ser raiz de um galho próprio atrás de
uma passiva descartável — é a habilidade de pico da árvore agora, não mais
enterrada Vidência.

## Varinha do Construtor (`dev.icaro.foodtooltips.builder`)

Item novo, só disponível via comando por enquanto (`/builderwand [player]`,
permissão `foodtooltips.admin`): clique direito num bloco já colocado estende
esse bloco em toda a linha ou coluna a partir dele — a direção depende da
face clicada (topo/base = coluna vertical, qualquer face lateral = linha
horizontal). Segue substituindo blocos de ar na direção escolhida até achar
um bloco que não seja ar, até o limite configurável
(`builder-wand.max-length`, padrão 64), ou — só na Sobrevivência — até
acabar aquele bloco no inventário do jogador. No Criativo não gasta nada
(mesma regra do próprio modo Criativo). `BuilderWandService#extend` copia o
`BlockData` inteiro do bloco clicado (não só o `Material`), então escadas,
troncos e outros blocos com orientação saem virados do jeito certo, não só
no padrão.

O item em si é um `Stick` identificado por uma flag na
`PersistentDataContainer` (não pelo nome, então renomear não quebra o
reconhecimento) — clicar com ele sempre cancela a interação padrão do bloco
(não abre baú/porta por engano), já que segurando a varinha a intenção é
sempre construir.

**Shift + clique esquerdo desfaz a última extensão**: `BuilderWandService`
guarda só a ação mais recente por jogador (bloco, lista dos blocos
colocados e se ela cobrou algo do inventário) — desfazer bota ar de volta
nesses blocos e, só se a extensão original tiver sido na Sobrevivência,
devolve a mesma quantidade daquele material (dropando no chão o que não
couber no inventário). É desfazer de 1 nível só, não uma pilha de
histórico. Shift + clique esquerdo com a varinha na mão também cancela a
quebra do bloco embaixo da mira, então não tem risco de minerar por
engano ao tentar desfazer.

A varinha é um `Stick` puro — que por padrão cairia em `JUNK_ITEMS` (Tier E)
no `ItemTierService`, já que `tierOf(Material)` não distingue "um Stick
comum" de "a varinha". Como ela é uma ferramenta única, não faz sentido
mexer na tabela de `Material` (isso rebaixaria todo Stick do jogo junto).
Em vez disso, `ItemTierService#forceTier(ItemMeta, ItemTier)` grava a tier
direto na `PersistentDataContainer` do item específico (chave separada da
usada pelo override de `Material` em `item-tiers`), e `BuilderWandService`
chama isso com `ItemTier.S` ao criar a varinha em `create()`. Na leitura,
`tierOf(ItemMeta, Material)` checa primeiro esse valor gravado no item antes
de cair no `tierOf(Material)` de sempre — é assim que o mesmo Stick de
sempre pode ter uma tier diferente sem afetar mais nenhum item no jogo.

## Mão do Destruidor (`dev.icaro.foodtooltips.destroyer`)

O espelho da Varinha do Construtor: item novo, também só disponível via
comando por enquanto (`/destroyerhand [player]`, permissão
`foodtooltips.admin`). Clique direito num bloco já colocado limpa esse
bloco e todo bloco do mesmo `Material` contíguo a ele na mesma direção
implícita na face clicada — a mesma convenção da varinha (topo/base =
coluna vertical, qualquer face lateral = linha horizontal), só que ao
invés de construir a partir do bloco clicado ela apaga a partir dele,
parando no primeiro bloco diferente (ar incluso) ou no limite configurável
(`destroyer-hand.max-length`, padrão 64). `DestroyerHandService#clear`
guarda o `BlockData` original de cada bloco removido antes de apagar, pelo
mesmo motivo que a varinha copia o `BlockData` ao construir: escadas,
troncos e outros blocos com orientação voltam do jeito certo se a ação for
desfeita.

Não mexe em drop table de verdade (sem olhar ferramenta, encantamento ou
loot table) — é uma troca crua material-por-material, espelhando a
simplificação que a própria varinha já faz pro lado de construir: no
Criativo não devolve nada (mesma regra do próprio modo Criativo), na
Sobrevivência devolve um item daquele `Material` pra cada bloco limpo
(dropando no chão o que não couber no inventário).

**Shift + clique esquerdo desfaz a última limpeza**, do mesmo jeito que na
varinha: bota os blocos de volta exatamente como estavam (mesmo
`BlockData`) e, só se aquela limpeza tiver devolvido itens (Sobrevivência),
tira de volta do inventário a mesma quantidade daquele material — melhor
esforço, se o jogador já não tiver mais o suficiente ele tira o que
sobrar. Também é desfazer de 1 nível só, e cancela a quebra do bloco
embaixo da mira do mesmo jeito.

O item em si é um `Bone` identificado por uma flag própria na
`PersistentDataContainer` (não pelo nome), e — assim como a varinha —
tem sua tier forçada pra `S` via `ItemTierService#forceTier` ao ser
criado, já que um `Bone` puro cairia em `C` por padrão (está em
`C_ITEMS` no `ItemTierService`).

## Arremesso de Espada: recarga menor, agora custa Mana; nível de desbloqueio

**Recarga reduzida** (`CombatTreeMath#swordThrowBaseCooldownMillis`): a curva
que ia de 30s (rank 1) a 3s (rank máximo) baixou pra 22s → 2s — um corte de
~27% em toda a escala.

**Novo custo de Mana** (`CombatTreeMath#swordThrowManaCost`): 35 no rank 1,
caindo até 15 no rank máximo (dominar a habilidade barateia o cast, mesmo
tema da própria recarga caindo por rank). `CombatAbilityService#spendSwordThrowMana`
saca de `PlayerStatsService#withdrawMana` — se não tiver Mana suficiente, o
arremesso simplesmente não sai (`SwordThrowListener#attemptThrow` mostra
"Mana insuficiente" na action bar) e, importante, **não gasta a recarga** por
um lançamento que nunca aconteceu. É a troca clássica de rebalanceamento:
antes só a recarga limitava o quanto você conseguia spammar a habilidade,
agora a Mana (um recurso de verdade, com regeneração própria) também entra
na conta. O tooltip da árvore mostra a nova linha "Custo de Mana" junto de
Dano/Recarga/Alcance.

**Nível de desbloqueio: 35 em vez de 60.** Arremesso de Espada fica no tier 4
da árvore (exige os dois "finalizadores" de ramo, Maestria Crítica e Segundo
Fôlego), então pelo requisito padrão por tier (`tier-level-requirements`)
precisaria de Nível de Combate 60 — dois tiers de grind depois de já ter os
pré-requisitos prontos. Como isso não fazia sentido pra uma habilidade que
deveria abrir *junto* com o topo dos dois ramos, `CombatAbilityService`
ganhou um mecanismo de override por habilidade (`LEVEL_REQUIREMENT_OVERRIDES`),
separado do requisito genérico por tier — só o Arremesso de Espada usa isso
por enquanto (fixo em 35 no código, não é uma opção de `config.yml`, já que é
uma decisão de design específica dessa habilidade, não um ajuste fino que
faça sentido variar por servidor) — qualquer outro nó que algum dia caia no
tier 4 continua usando o requisito padrão de 60 normalmente, o override é
por habilidade, não por tier inteiro.

**A posição na grade também mudou**, não só o número: o slot do Arremesso de
Espada saiu de 22 (linha do tier 4, ao lado do medidor "60") pra 31 (linha do
tier 3, ao lado do medidor "35"), ficando entre Maestria Crítica (coluna 2) e
Segundo Fôlego (coluna 5) — o ponto exato onde as duas correntes se
encontram. Sem isso, o ícone continuava desenhado na linha errada mesmo
depois do requisito numérico mudar, o que é enganoso: o jogador vê o medidor
de Nível de Combate daquela linha mostrando 60, mas a habilidade ao lado só
precisa de 35 de verdade.

## Varinha e Mão do Destruidor: modo Linha/Coluna ou Face inteira

As duas ferramentas ganharam um **menu de configuração**: clique esquerdo
(sem agachar) nelas agora abre um inventário de 1 linha com duas opções —
**Linha/Coluna** (o comportamento de sempre, estende/limpa só na direção da
face clicada) e **Face inteira (parede/chão)** (novo: preenche/limpa toda a
área conectada da parede ou chão que você está olhando). A opção marcada
com ✔ e brilho é o modo atual; clicar na outra troca na hora. O modo fica
gravado no próprio item (`BuilderWandService.FillMode`/
`DestroyerHandService.FillMode`, uma segunda chave na
`PersistentDataContainer` separada da que identifica a ferramenta), não no
jogador — cada varinha/mão guarda sua própria configuração.

**Modo Face inteira** é um flood-fill (busca em largura) na malha
perpendicular à face clicada: pra Varinha, começa um bloco além do clicado
(mesmo ponto de partida do modo Linha) e espalha por ar contíguo,
preenchendo cada bloco com o mesmo `BlockData` do bloco original; pra Mão do
Destruidor, começa no próprio bloco clicado e espalha por blocos contíguos
do mesmo `Material`, limpando cada um. Os dois modos reusam o mesmo limite
`max-length` do `config.yml` como teto de blocos processados (achatado, não
elevado ao quadrado — uma parede de 64×64 seria grande demais pra processar
de uma vez), então uma parede/chão muito grande só preenche/limpa até esse
teto e para, sem estourar performance. Undo (shift + clique esquerdo)
funciona igual nos dois modos, já que ele só depende da lista de blocos
afetados guardada na última ação — não importa se ela veio de uma linha ou
de um flood-fill.

Com essa mudança, clique esquerdo *sem* agachar (que antes simplesmente não
fazia nada, deixando a quebra normal do bloco vazar por baixo do cancelamento
de evento) agora sempre abre o menu e cancela a interação — fechando de
quebra uma pequena inconsistência onde seria possível minerar um bloco por
engano segurando a ferramenta sem querer usá-la.

## Correção do flood-fill (modo Face) e menu ampliado

**Bug corrigido: o modo Face não preenchia/limpava a área direito.** O
flood-fill guardava os blocos já visitados num `HashSet<Block>` — mas
`Block#getRelative` devolve uma instância nova a cada chamada, e depender do
`equals`/`hashCode` dela pra deduplicar é uma pegadinha conhecida da API do
Bukkit (não é garantido comparar por coordenada em toda versão). Na prática
isso fazia o algoritmo ficar "quicando" entre um punhado de blocos vizinhos
sem nunca se espalhar de verdade pela parede/chão. Trocado por um record
interno `Pos(x, y, z)` como chave do `HashSet` — comparação por valor
garantida, sem depender de nenhum comportamento específico do `Block`.

**Novo controle de Alcance no menu**: além de Linha/Face, o menu de
configuração (clique esquerdo) ganhou um terceiro item — uma luneta
mostrando o alcance atual, clique esquerdo aumenta e clique direito diminui,
ciclando entre potências de 2 (8, 16, 32...) até o teto configurado em
`max-length`. Isso fica gravado por item, igual ao modo — cada varinha/mão
pode ter seu próprio alcance, sem precisar mexer no `config.yml` nem
reiniciar o servidor. O valor nunca passa do `max-length` do servidor, só
pra baixo dele.

**O menu não fecha mais sozinho.** Antes, escolher Linha ou Face fechava o
inventário na hora; agora qualquer clique (modo ou alcance) só atualiza os
itens do próprio menu, que continua aberto — dá pra ajustar várias
configurações na mesma sessão sem precisar reabrir o menu a cada mudança.
Fecha normalmente com ESC ou clicando fora, como qualquer inventário.

**Vidro cinza em vez de preto** nos três menus que usavam
`BLACK_STAINED_GLASS_PANE` como preenchimento (o menu de configuração da
Varinha, o da Mão do Destruidor, e a Árvore de Combate) — alinhando com o
`GRAY_STAINED_GLASS_PANE` que todo o resto dos menus do plugin já usava
como padrão.

## Menu de configuração: grade de 3 linhas, clique no ar confiável

**Menu ampliado pra 3 linhas (27 slots)**, com os três controles (Linha,
Face, Alcance) centralizados na linha do meio, em vez do inventário de 1
linha só de antes.

**Bug corrigido: clique no ar não abria o menu.** O gatilho do menu/desfazer
usava `PlayerInteractEvent` com `Action.LEFT_CLICK_AIR` — mas esse evento no
Bukkit é jogado de forma "melhor esforço"/limitada pro caso de clicar no ar
(diferente do clique num bloco, que é confiável), então nem todo balançar de
braço sem alvo chegava a disparar o listener. Trocado pelo
`PlayerAnimationEvent` (o evento de "balançar o braço" puro) como gatilho
principal — esse dispara em todo clique esquerdo, com ou sem bloco na mira,
sem exceção. O `PlayerInteractEvent` continua sendo usado, mas só pra
cancelar a quebra do bloco quando o clique acontece em cima de um (a
ação de desfazer/abrir menu em si já rodou pelo evento de animação).

## Modo Face da Varinha corrigido: agora copia a parede existente

**Bug corrigido: o modo Face inundava o ar aberto sem limite natural.**
Antes, `extendFace` fazia flood-fill direto na camada de AR a ser
preenchida — mas ar sem nada atrás não tem beirada natural pra parar, então
clicar num bloco isolado (sem parede real por trás) fazia o preenchimento
crescer feito um losango (o formato clássico de flood-fill BFS em espaço
aberto) até bater no limite de Alcance, sem guardar nenhuma relação com uma
parede de verdade.

A correção muda a ordem das coisas: primeiro `extendFace` rastreia a forma
**real** da parede/chão existente (flood-fill pelo mesmo `Material` do
bloco clicado, contíguo — o mesmo algoritmo que `DestroyerHandService`
já usa pra decidir o que limpar), *depois* pinta uma cópia dessa forma na
camada imediatamente além dela, só onde tiver ar. Isso naturalmente limita
o preenchimento ao tamanho real da parede (que sempre tem uma borda física
concreta), em vez de inundar o vazio sem nenhuma referência.

## Modo Linha: Mão do Destruidor anda ao longo da parede, Varinha continua na direção da face

**Bug corrigido (só na Mão do Destruidor): clicar numa face lateral só
limpava 1 bloco.** A direção do modo Linha sempre foi literalmente a face
clicada (clicar na face leste = anda pra leste) — o que faz sentido pra uma
coluna vertical (clicar topo/base) ou uma linha "pra fora" de um ponto
isolado (um pilar, uma ponte), mas numa parede plana de 1 bloco de
espessura, andar "pra fora" da face lateral significa furar através dela —
e como ela só tem 1 bloco de espessura, a linha parava imediatamente depois
desse único bloco.

Pra Mão do Destruidor, clicar numa face lateral (não topo/base) agora faz o
modo Linha andar **ao longo do plano da própria parede** em vez de furar por
ela. `DestroyerHandService#lineDirection` escolhe entre as duas direções
possíveis nesse plano (ex.: Norte ou Sul, pra uma parede virada
Leste/Oeste) checando qual delas **continua de verdade com o mesmo
`Material`** do bloco clicado — é limpeza, então dá pra olhar o estado real
do mundo em vez de adivinhar. Só cai pro critério de produto escalar entre a
direção do olhar do jogador e o vetor de cada `BlockFace` candidata quando
isso é ambíguo (as duas direções continuam com o mesmo material, ex.: no
meio de uma parede comprida) ou indiferente (nenhuma das duas continua,
ex.: um bloco isolado — a limpeza só alcançaria 1 bloco de qualquer jeito).
Clicar topo/base continua sem mudança — ainda uma coluna vertical simples.

**A Varinha do Construtor não ganhou essa mudança.** Uma primeira versão
aplicou o mesmo critério (olhar + produto escalar) simetricamente às duas
ferramentas, mas pra construir isso não faz sentido: o modo Linha da Varinha
estende pra dentro do **ar**, então não existe "material real que continua"
pra checar — só o palpite do olhar, que adivinhava errado com frequência e
fazia a Varinha construir pro lado errado do bloco clicado. A Varinha
manteve (voltou a) o comportamento original: o modo Linha sempre estende
literalmente na direção da face clicada, sem heurística nenhuma. As duas
ferramentas resolvem o mesmo problema de formas diferentes porque uma
enxerga o mundo real (limpar) e a outra não (construir no vazio).

## Novo preset de Alcance: Ilimitado

O menu de Alcance ganhou uma última opção acima do teto configurado em
`max-length` (padrão 64): **Ilimitado** (ícone de olho de ender, com um
aviso em vermelho no lore). Ela pula o clamp normal de `[1, max-length]` —
é a única forma de passar do teto do servidor sem editar o `config.yml`.
Continua sendo uma ferramenta administrativa (permissão `foodtooltips.admin`
nos comandos `/builderwand` e `/destroyerhand`), então é um opt-in
deliberado, não um buraco de segurança.

Internamente `UNLIMITED` não é literalmente infinito. Além do risco óbvio
de loop sem fim (`Integer.MAX_VALUE`: o modo Linha da Varinha só para ao
encontrar um bloco não-ar, então apontar pro céu aberto em modo Criativo
giraria o loop até estourar o limite de verdade), tem um segundo risco mais
sutil: colocar/remover cada bloco é síncrono, na mesma tick, na thread
principal do servidor — cada `setBlockData`/quebra de bloco pode disparar
recálculo de física e luz, então mesmo um número "grande" como 10.000
blocos numa ação só já é suficiente pra travar o servidor por um instante
perceptível, independente de virar loop infinito ou não. Por isso o valor
final é bem mais conservador: **1.000 blocos** por ação — generoso (várias
construções/paredes inteiras de uma vez), mas curto o bastante pra não
gerar uma trava sentida pelos jogadores.

## Bug corrigido: HP caindo pra 100 ao voltar do mapa / trocar de mundo

**Causa raiz**: `CombatListener#join` chamava `stats.applyBaseHealth` (zera a
base de Vida Máxima pro valor puro do `config.yml`, 100) *antes* dos bônus —
Bestiário (`bestiary.applyBonusHealth`) e Nível Global
(`GlobalLevelService#applyHealth`) — serem reanexados. No instante entre
esses dois passos, a Vida Máxima efetiva do jogador já caiu pra 100 sem os
bônus ainda em cima, e o próprio motor do jogo corta a Vida atual pro novo
teto automaticamente assim que o atributo muda — um corte que não volta
sozinho quando os bônus reaparecem alguns milissegundos depois. Pior: o
bônus do Nível Global nem era reanexado nesse método — só o do Bestiário —
então esse pedaço ficava perdido até o jogador ganhar XP Global de novo.

Isso disparava toda vez que o jogador reconectava (`PlayerJoinEvent`), e
muito provavelmente também ao trocar de mundo via Multiverse-Core/portais
sem desconectar, caso a troca de mundo dispare uma reconstrução da
`AttributeInstance` que derruba os modificadores transitórios de Vida
Máxima do mesmo jeito.

**Correção**: `CombatListener#reapplyHealthStack` agora reaplica base +
Bestiário + Nível Global (nessa ordem, incluindo o bônus que faltava) e só
então define a Vida do jogador uma única vez, no final — nunca no meio do
caminho, onde um vácuo momentâneo sem os bônus faria o motor do jogo cortar
a Vida sozinho. Usado no join **e** num novo handler de
`PlayerChangedWorldEvent` (cobre troca de mundo em Multiverse/portais sem
precisar desconectar); o laço de `onEnable` que reaplica tudo pros
jogadores já online num `/reload` recebeu a mesma proteção contra o corte,
mas preservando o valor anterior (não cura — um `/reload` no meio de uma
luta não devia curar todo mundo de graça).

**Vida cheia ao aparecer no mapa** (`stats.heal-to-full-on-map-enter` no
`config.yml`, `true` por padrão): em vez de só preservar a Vida de antes,
o padrão agora é curar o jogador por inteiro toda vez que ele aparece num
mapa — join no servidor ou troca de mundo, os dois casos que
`reapplyHealthStack` já cobre — como um "checkpoint" de hub/lobby. Desliga
essa opção pra voltar ao comportamento de só preservar o que já era (ainda
protegido contra o corte espúrio de qualquer forma, só sem o heal grátis).

**Sobre o Gamemode mudando ao trocar de mundo**: isso **não é o
IcarusRPG** — o plugin não toca em `GameMode` em lugar nenhum do código
(só *lê* pra decidir mecânicas da Varinha/Mão, nunca escreve). É quase
certamente o próprio Multiverse-Core aplicando um gamemode forçado por
mundo (configurável em `worlds.yml`, ou via `/mv modify <mundo> set
gamemode <valor>`) — o fix é limpar essa config lá (`/mv modify <mundo> set
gamemode` sem valor, ou apagar a linha `gamemode:` do `worlds.yml` daquele
mundo específico), não algo pra corrigir por aqui. Deliberadamente não
implementei um "lembra e restaura o gamemode" no plugin pra contornar isso
— brigar com a configuração de outro plugin desse jeito é frágil e corre o
risco de reverter até uma troca de gamemode legítima (um admin te colocando
em Creative, por exemplo). Se depois de ajustar o Multiverse o problema
persistir, aí sim vale revisitar.

## Correções na Ferocity e no PvP, partículas de acerto extra, `/resetstats`

**Bug corrigido: os hits extras da Ferocity ignoravam Defesa e Segundo
Fôlego.** Em vez de gerar um `EntityDamageEvent` de verdade, o código
chamava `target.setHealth()` direto:

```java
double newHealth = Math.max(0.0, target.getHealth() - extraDamage);
target.setHealth(newHealth);
```

Isso pulava completamente `ArmorDefenseListener` (só reage a
`EntityDamageEvent` de verdade) e `CombatListener#secondWind` — um mob
"tanque" com Defesa alta tomava o hit extra da Ferocity cheio, sem nenhuma
mitigação, e o jogador não conseguia usar Segundo Fôlego pra sobreviver a
um hit extra que seria fatal. Corrigido reaproveitando
`CombatAbilityService#dealAbilityDamage` (o mesmo mecanismo que o Arremesso
de Espada já usa) pra cada hit extra: chama `target.damage()` de verdade,
sinalizado (`isAbilityDamageInFlight`) pra `CombatListener#damage` não
reprocessar o hit no stack de multiplicadores nem deixar um hit extra rolar
mais hits extras — mas Defesa e Segundo Fôlego, que reagem ao evento de
dano em si (não ao código que o disparou), agora funcionam normalmente.

**Novo: linha de partículas vermelhas em cada hit extra da Ferocity**
(`MobVisualService#ferocityHit`) — uma linha curta de `Particle.DUST`
vermelho do atacante até o alvo, uma por hit extra. Motivo: numa luta
cheia de números de dano, animação de hit do mob e efeitos de outros
jogadores, um número que aparece e some em menos de um segundo passa
despercebido; uma linha visível deixa claro que o hit extra realmente
aconteceu.

**Bug/lacuna corrigida: PvP não usava o mesmo stack de dano do PvE.** Antes:

```java
if (target instanceof Player) {
    e.setDamage(e.getDamage() * this.global.strengthMultiplier(p));
    return;
}
```

Só o multiplicador de Strength do Nível Global se aplicava — nada de
crítico, Ferocity ou multiplicador de dano por Nível de Combate, que o PvE
sempre recebeu. Com espadas batendo 20-40 de base e PvE multiplicando isso
bastante por nível/crítico, um build endgame batia desproporcionalmente
mais fraco em outro jogador do que num mob. Unificado: PvP agora passa pelo
mesmo stack (nível, crítico, Ferocity, multiplicador de habilidades,
Strength), exceto o bônus por tipo de mob do Bestiário, que não faz sentido
contra jogador. Configurável em `combat.pvp-full-damage-stack` no
`config.yml` (`true` por padrão) pra quem preferir voltar à fórmula antiga
(só Strength) sem recompilar.

**Limpeza**: um comentário em `SwordThrowListener` mencionava
"CombatListener skips Cleave's splash" — não existe (nem nunca existiu,
até onde os fontes mostram) nenhuma habilidade "Cleave"/dano em área no
projeto. Comentário desatualizado/confuso, reescrito pra descrever o que o
código realmente faz.

**Novo comando `/resetstats <player>`** (`foodtooltips.admin`): reseta
*todos* os status que o plugin já guardou daquele jogador pros valores
iniciais — Mana/Vitalidade, Nível/XP de Combate, skills gerais, ranks da
Árvore de Combate, Valor de Combate, Moedas, Nível Global (com checkpoints
e a flag de migração) e abates/marcos do Bestiário. Implementado de forma
deliberadamente "bruta": em vez de zerar sistema por sistema (uma lista que
precisaria ser atualizada toda vez que um stat novo for adicionado no
futuro), remove toda chave da `PersistentDataContainer` do jogador sob o
namespace `"foodtooltips"` — o namespace literal fixo que todo stat de
jogador do plugin usa (metadado de item, como Tier ou Dano de Espada, mora
no `ItemMeta` do item sob um namespace por-instância-de-plugin, nunca no
jogador, então fica intocado). Depois disso, reaplica o pipeline de
atributos (Vida base, Mana/Vitalidade, Attack Speed, Defesa, cura total)
pro jogador já sair do comando com tudo refletido, sem precisar relogar.

**Não é tocado**: inventário/itens, XP/nível vanilla, posição, gamemode.

## Mecânica de mochilas removida

Todo o sistema de mochilas extras (a de Combate, que vivia na árvore de
habilidades, e as outras 6 por skill geral — Mineração, Pesca, Agricultura,
Coleta, Encantamento, Alquimia) foi removido do IcarusRPG por completo:
`BackpackService`, `BackpackListener` e `BackpackType` (pacote `skills`)
saíram do projeto, os 6 nós de capacidade da árvore de Combate
(`CombatAbility.BACKPACK_1`..`BACKPACK_6`) e o ramo `CombatBranch.STORAGE`
que os continha deixaram de existir, e toda referência a eles no menu de
Skills, na Árvore de Combate e no `/resetstats` foi removida junto.

Motivo: essa funcionalidade de armazenamento vai virar um plugin próprio,
separado do IcarusRPG (inspirado no mod Sophisticated Storage), então não
faz sentido o IcarusRPG continuar oferecendo sua própria versão dela.
Servidores que já tinham mochilas em uso vão perder o acesso aos itens
guardados nelas — os arquivos `.yml` da pasta `backpacks/` na pasta de
dados do plugin não são apagados automaticamente por essa mudança (o
código que os lia é que não existe mais), então quem quiser recuperar o
conteúdo antes de descartar precisa fazer isso manualmente enquanto os
arquivos ainda estão lá.

## Dependência: IF (Inventory Framework) pra menus

Adicionado `com.github.stefvanschie.inventoryframework:IF` como
dependência (Maven Central) pra construir os menus de inventário
(`ChestGui`, `StaticPane`, `GuiItem`) em vez de `Inventory` +
`InventoryClickEvent`/`InventoryDragEvent`/`InventoryCloseEvent` cru. Como
o IF não é um plugin que já vem instalado no servidor, ele é *shadado* (e
relocado pra `dev.icaro.foodtooltips.libs.inventoryframework`, pra não
colidir com uma versão diferente que outro plugin no mesmo servidor também
tenha shadado) direto no jar final pelo `maven-shade-plugin` — o jar
gerado passou de ~310 KB pra ~1,9 MB, mas continua sendo o único arquivo
que precisa ir pra pasta `plugins/` do servidor.

**Pinado em `0.12.0`, não a última (`0.12.1`)**: a `0.12.1` adicionou
processamento de anotações de clique/drag/close no construtor de `Gui`
(`Gui#processMethodAnnotations`) que lê `method.getParameterTypes()[0]`
sem checar se o método tem 0 parâmetros antes — e `ChestGui` declara vários
métodos de 0 parâmetro próprios (`copy()`, `getRows()`...), então **toda**
construção de `ChestGui` derruba com `ArrayIndexOutOfBoundsException`,
com ou sem anotação em uso (foi assim que apareceu em produção: `/levelcolor`
crashando 100% das vezes). Confirmado que o bug ainda está no `master` do
IF em 28/08/2026 — bug deles, não nosso. Voltar pra `0.12.1`\+ só quando
upstream corrigir; a API que usamos (`ChestGui(int, String, Plugin)`,
`StaticPane`, `GuiItem`, `Slot.fromXY`) é idêntica nas duas versões.

`LevelColorMenuService` foi o primeiro menu migrado, como prova de
conceito: o `Set<UUID> viewers` manual e os três `@EventHandler` de
click/drag/close saíram, e cada item do menu agora carrega seu próprio
clique (`GuiItem(item, event -> ...)`) — o IF cancela sozinho clique e
drag dentro do menu e sabe de quem é a `Gui` aberta sem precisar de
rastreamento próprio. Os outros menus (`SkillsMenuService`,
`CombatTreeMenuService`, `BestiaryMenuService`, `MiningMenuService`) ainda
estão no estilo manual antigo — migração deles fica pra depois.

## Skills gerais agora dão bônus de atributo, não só Fortune

`GeneralSkillService` ganhou três bônus novos, cada um por nível da skill
(`bonusHealth`, `bonusStrength`, `bonusMaxMana`), no mesmo espírito da
Fortune de Mineração/Agricultura/Coleta que já existia:

- **Agricultura (Farming) e Pesca (Fishing)**: +2 Vida Máxima por nível
  cada (Agricultura mantém sua Fortune de +4 também). Aplicado como
  `AttributeModifier` em `MAX_HEALTH` (`GeneralSkillService#applyBonusHealth`),
  chamado junto com o de Bestiário e Nível Global sempre que a Vida Máxima
  é re-derivada (join, troca de mundo, `/resetstats`, e agora também em
  todo ganho de XP/level up e em `/setskilllevel`).
- **Coleta (Foraging)**: +1 Strength por nível (mantém sua Fortune de +4
  também). Strength não tem attribute modifier próprio — é só um número
  somado dentro de `GlobalLevelService#snapshot` junto com o Strength de
  Nível Global, então aparece automaticamente em tudo que já lia Strength
  (multiplicador de dano, HUD de status).
- **Alquimia e Encantamento**: +1 Mana Máxima por nível cada. Somado em
  `PlayerStatsService#effectiveMaxMana`, junto com a Inteligência.

O menu de cada skill (`/skills` → skill individual) mostra a recompensa de
atributo de cada nível na lore do nó — igual já fazia pra Fortune e pro
+1 Defesa da Mineração.

## Estrela do Nether fixa na hotbar — atalho pro Menu

`SkillsStarService` + `SkillsStarListener` (pacote `skills`): toda vez que
um jogador entra, o **último slot da hotbar** (o "9" da forma como o jogo
numera, índice 8 na API) recebe uma Estrela do Nether chamada "★ Menu",
identificada por uma tag na própria `ItemMeta` (mesmo padrão de
`BuilderWandService#isWand`) — **qualquer clique nela** (esquerdo ou
direito, no ar ou num bloco/objeto, segurando ela como bússola/mapa, ou
clicando nela dentro do inventário aberto) abre o menu de Habilidades
(`SkillsMenuService#openMain`), sem precisar digitar `/skills` ou saber o
atalho de shift+trocar-de-mão. Clique esquerdo usa `PlayerAnimationEvent`
em vez do `LEFT_CLICK_AIR` de `PlayerInteractEvent` (que é best-effort e
não dispara toda vez que balança o braço no ar sem nada por perto) — mesma
solução que `BuilderWandListener#swing` já usava. Fica na hotbar (não na
mochila) de propósito: assim dá pra selecionar e usar com um scroll/tecla
numérica/toque, sempre visível na tela — o que importa principalmente pra
quem joga no console ou celular.

**Irremovível e intransferível**: qualquer clique/drag que envolva a
estrela (pegar, mover, dar shift-click, hotbar-swap, trocar de mão com F)
é cancelado (`SkillsStarListener`), ela é retirada da lista de drops se o
jogador morrer com `keepInventory` desligado, e é sempre re-concedida no
login, no respawn e num `/reload` com jogadores já online — sem tag
correta, um Nether Star qualquer não conta como a estrela e não abre
nada. Se o slot já tiver algo quando a estrela chega (jogador que já
existia antes dessa mudança), aquele item é reencaixado em outro slot
livre da mochila (ou cai no chão se não couber) em vez de ser apagado.
`SkillsStarService#ensure` também varre a mochila inteira procurando
cópias perdidas da estrela em qualquer outro slot e some com elas antes
de garantir a do slot certo — sem isso, todo jogador que já tinha
recebido a estrela na primeira versão (que vivia no primeiro slot da
mochila, não da hotbar) ficaria pra sempre com duas estrelas idênticas,
uma órfã sem função alguma e impossível de descartar sozinho.

## Mensagens de level-up das skills gerais no mesmo padrão do Combate

As mensagens de subir de nível de Agricultura/Pesca/Mineração/Coleta/
Alquimia/Encantamento (`GeneralSkillListener#levelUpMessage`) agora usam a
mesma "caixa" de linhas do level-up de Combate/Bestiário
(`CombatListener#levelUpMessage`/`#milestoneMessage`): separador, título
com nível antes → depois, uma linha com a recompensa de atributo realmente
ganha nesse level-up (Fortune/Vida/Força/Mana, multiplicada pela
quantidade de níveis subidos de uma vez), a linha de XP de Nível Global, e
(só pra Mineração cruzando o nível 3) o aviso de Vein Miner desbloqueado.
Antes era uma única linha simples.

De passagem: a lore do menu de Mineração alegava "+1 Defesa por nível", o
que não é mais verdade desde que Defesa passou a vir inteiramente do
equipamento (`ArmorDefenseService`, não mais do nível de Mineração) — essa
linha errada foi removida do menu e nunca apareceu na mensagem de level-up
nova. Os números de bônus por nível (Fortune/Vida/Força/Mana) agora moram
só em `GeneralSkillService` (`fortunePerLevel()`/`healthPerLevel()`/
`strengthPerLevel()`/`maxManaPerLevel()`), lidos tanto pelo menu quanto
pela mensagem de level-up, em vez de cada um repetir o número por conta
própria.

## Durabilidade de todo item multiplicada por 5

`DurabilityService` + `DurabilityListener` (pacote `item`): a Durabilidade
Máxima de todo item danificável — ferramentas, armas, armaduras, arco,
besta, tridente, maça, escudo, vara de pescar, elytra, tudo que
`Material#getMaxDurability()` reporta maior que zero — é multiplicada por
`items.durability-multiplier` (padrão 5) no config.yml. Mesmo padrão
idempotente do `ItemTierService#applyItemTiers`: marcado uma vez por item
via PDC, aplicado no login e reaplicado a cada tick do HUD, então alcança
qualquer item crafado/minerado/comprado/dado depois, sem precisar de um
hook dedicado de craft/pickup/clique. O dano atual do item é multiplicado
junto com o máximo, então uma ferramenta já 80% gasta continua 80% gasta
(proporcionalmente) em vez do multiplicador dar durabilidade de graça pra
item já usado.

## Mineração também dá Defesa por nível

`GeneralSkillService#bonusDefense`: Mineração agora concede +1 Defesa por
nível, além da sua Fortune de +4/nível de sempre. Diferente dos outros
bônus de skill geral (que ou são atributos independentes como Strength/
Mana, ou precisam de `AttributeModifier` como Vida Máxima), Defesa nunca
teve modificador próprio — é só um número somado sob demanda em
`ArmorDefenseService#defense`, junto com a soma do equipamento — então o
bônus de Mineração passa a valer automaticamente em tudo que já lia
`defense()`: a redução de dano de verdade (`ArmorDefenseListener`), o HUD,
e a tela de Status de Combate. `ArmorDefenseService` ganhou uma referência
opcional a `GeneralSkillService` (`general(GeneralSkillService)`, mesmo
padrão de wiring pós-construção que `PlayerStatsService#general` já usa)
pra isso — mobs continuam sem bônus algum, só jogadores.

(Nota: uma versão antiga do menu de Skills já alegava "+1 Defesa por
nível" pra Mineração, mas isso não era real desde que Defesa passou a vir
só do equipamento — essa lore foi removida numa limpeza anterior por ser
falsa. Agora ela é real de novo, e a lore/mensagem de level-up foram
reescritas pra refletir isso.)

## Correção: itens do mesmo tipo não empilhavam (ex.: Carne Podre)

Dois stacks idênticos de um mesmo item (mesmo nome, mesma lore, tudo igual
visualmente) às vezes ficavam presos como slots separados, nunca se
juntando. Causa: tanto `ItemTierService` (tag de Tier) quanto
`FoodTooltipListener` (atributos de comida) reescrevem a lore de um item
*uma vez*, no primeiro tick em que o veem, cada um no seu próprio
cronograma de eventos - se dois stacks do mesmo item passam por essas
duas reescritas em **ordens diferentes** entre si (ex.: um primeiro ganha
a tag de Tier e depois os atributos de comida, o outro na ordem inversa),
as linhas de lore ficam no mesmo conteúdo mas em **ordem diferente** pra
sempre, e o jogo nunca mais considera os dois stacks "iguais" pra
empilhar.

`ItemStackUtil.coalesce` (extraído de dentro do `ItemTierService`, que já
tinha essa lógica só pra si mesmo) agora roda tanto em
`ItemTierService#applyItemTiers` quanto em `FoodTooltipListener#update` -
depois de cada rodada de reescrita de lore, varre o inventário mesclando
qualquer par de stacks que já bateu igual, então mesmo que a ordem das
linhas tenha ficado diferente por um tick, a primeira reescrita que
finalmente igualar as duas já os re-junta.

## Aba de cada skill geral em Status & Equipamento (não só Fortune)

`SkillsMenuService#skillBonusItem` substitui o antigo `fortuneItem`: a
tela "Status & Equipamento" (clique na cabeça no menu de Habilidades)
agora tem uma aba pra cada uma das 6 skills gerais — Mineração,
Agricultura, Pesca, Coleta, Alquimia e Encantamento — não só as 3 que
davam Fortune antes (Pesca, Alquimia e Encantamento estavam faltando por
completo). Cada aba mostra todo bônus de atributo daquela skill (Fortune
quando aplicável, +Defesa/Vida/Força/Mana quando aplicável) com a mesma
linha cinza de fonte que o Combat Stats ganhou antes ("Nível N ×
X/nível"), pro detalhamento de origem valer em todas as skills, não só em
Combate.

## Correção: painéis de vidro do menu empilhando em vez de preencher os slots vazios

Regressão introduzida pela própria correção de empilhamento acima: o
`ItemStackUtil.coalesce` recém-adicionado em `FoodTooltipListener#update`
rodava em cima de `p.getOpenInventory().getTopInventory()` sem
distinguir um inventário de verdade (um baú, por exemplo) de um menu
virtual do próprio plugin. Todo menu criado por
`SkillsMenuService#inv` (Skills, Status & Equipamento, cada aba de
skill...) preenche os 54 slots com a **mesma referência** de
`ItemStack` de `GRAY_STAINED_GLASS_PANE` pra cada slot vazio - então,
toda vez que `refresh()` rodava com um desses menus aberto (o que
acontece em quase qualquer ação do jogador: entrar, clicar, trocar item
na mão...), o coalesce enxergava dezenas de "stacks" idênticos e os
fundia numa pilha só, esvaziando o resto do fundo do menu.

`refresh(Player p)` agora só roda a passagem (tooltip + coalesce) sobre
o inventário do topo quando `topInventory.getHolder() != null` - o
discriminador entre um inventário real, com dono (baú, baú de ender...)
e um headless criado via `Bukkit.createInventory(null, ...)` como os
menus do plugin. Baús e outros containers de verdade continuam
recebendo tooltip de comida e correção de empilhamento normalmente; os
menus do plugin nunca mais têm o próprio fundo decorativo mexido.

## Bônus de Alquimia/Encantamento agora é Inteligência, não Mana Máxima direto

Cada ponto de Mana Máxima já vinha, na prática, de um ponto de
Inteligência (`PlayerStatsService#effectiveMaxMana` soma
`baseIntelligence` + bônus de skill ao Mana Máximo base), então o bônus
de nível de Alquimia/Encantamento fazia mais sentido nomeado como
Inteligência - é exatamente isso que ele já era por baixo dos panos, só
que exibido com o nome errado (e nem aparecia na própria linha de
Inteligência do Combat Stats, escondido só dentro do total de Mana
Máxima).

`GeneralSkillService#bonusMaxMana`/`maxManaPerLevel` foram renomeados
para `bonusIntelligence`/`intelligencePerLevel` (mesmo valor, +1/nível
por skill); `PlayerStatsService` ganhou `effectiveIntelligence(Player)`
centralizando "Inteligência base + bônus de Alquimia/Encantamento", usado
tanto por `effectiveMaxMana` (o Mana Máximo final não muda) quanto pelo
snapshot de `PlayerStats#intelligence()` (que agora inclui o bônus,
antes só mostrava a base). Todo texto de "+1 Mana Máxima" (menu de
skills, mensagem de level-up, aba de skill em Status & Equipamento) virou
"+1 Inteligência", e a linha-fonte da Inteligência no Combat Stats passou
a discriminar "Base X" + "Alquimia/Encantamento +Y", no mesmo padrão das
outras linhas de origem.

## Armas Lendárias (`/rpgitems`) e o novo tipo de arma Adaga

Novo comando admin-only `/rpgitems` (`foodtooltips.admin`) abre um menu de
54 slots (`LegendaryItemsMenuService`, mesmo padrão visual dos menus de
`SkillsMenuService`) com um item por arma lendária - clicar entrega uma
cópia pra você mesmo. Por enquanto essas armas só existem via esse menu,
sem craft nem drop de mob (a ideia é que outras fontes cheguem depois,
sem precisar redesenhar nada disso).

`LegendaryWeapon` (`dev.icaro.foodtooltips.item.legendary`) cataloga as 6
armas, cada uma com Tipo (Adaga ou Espada Longa), um `ItemTier` (o mesmo
sistema de Tiers usado em todo o resto do jogo - ver "Tier no lugar de
Raridade" abaixo), Ataque base e, quando aplicável, Agilidade:

- **Presa de Veneno de Kasaka** (Adaga, Tier C) - +25 Ataque. A cada
  acerto, 25% de chance independente de Paralisia (Slowness bem alto +
  Jump Boost negativo, ~3s - trava o alvo sem precisar de
  teleporte/cancelamento de movimento manual) e 25% de chance de
  Sangramento (2% da vida máxima do alvo por segundo, por 4 segundos;
  empilha até 3 vezes ao mesmo tempo - um 4º proc enquanto já tem 3 ativos
  simplesmente não faz nada).
- **Matador de Cavaleiros** (Adaga, Tier B) - +75 Ataque. +25% de dano
  contra qualquer alvo (jogador ou mob) usando pelo menos 1 peça de
  armadura.
- **Adaga de Baruka** (Adaga, Tier A) - +110 Ataque, +50 Agilidade (a stat
  é nova: converte em Velocidade de Movimento enquanto a adaga está na
  mão, +0.001 por ponto de Agilidade sobre a base vanilla de 0.1 - 1
  Agilidade = +1% de Velocidade, ver a seção de Agilidade/Velocidade mais
  abaixo - +50 Agilidade vira +50% de Velocidade).
- **Adagas do Rei Demônio** (Adaga, Tier S) - +220 Ataque. Two as One:
  +0.5 de dano adicional por ponto de Strength do usuário, somado antes
  do resto da pilha de multiplicadores de combate (então também se
  beneficia de crítico etc., como o resto do dano da arma).
- **Espada Longa do Rei Demônio** (Espada Longa, Tier S) - +350 Ataque,
  +2 blocos de alcance de ataque em vez da penalidade de Adaga (é uma
  espada longa, faz sentido alcançar mais que uma espada comum, não
  menos). Storm of White Flames: tecla F (mesmo gatilho de Arremesso de
  Espada - ignorado agachado), custa 40 de Mana, 30s de recarga; encontra
  todo LivingEntity num raio de 4 blocos de onde você está mirando (até
  20 blocos de alcance) e crava um raio cosmético direto em cada um deles
  (até 6, os mais próximos primeiro) - não em pontos aleatórios da área -
  aplicando 100 de dano via `CombatAbilityService#dealAbilityDamage`
  (mesmo mecanismo do Arremesso de Espada, pra não reprocessar pela pilha
  de multiplicadores de golpe corpo a corpo). Sem nenhum alvo na área, cai
  um raio só de efeito no ponto mirado, como feedback.
- **Fúria de Kamish** (Adaga, Tier S) - Ataque escala com Strength (1500
  base + 1 por ponto de Strength do usuário) em vez de um número fixo.
  "Alterar o peso como quiser" é implementado como uma isenção fixa da
  penalidade de alcance de Adaga - o alcance dela é o de uma espada
  comum.

**Mecânica universal de Adaga** (`WeaponType.DAGGER`, todas exceto a
Espada Longa do Rei Demônio e - só na parte do alcance - a Fúria de
Kamish): -1 bloco de alcance de ataque, aplicado como um
`AttributeModifier` de -1 na mesma attribute de alcance
(`entity_interaction_range`) que `PlayerStatsService#applySwingRange` já
usa, mas escopado a `EquipmentSlotGroup.MAINHAND` no próprio item - soma
normalmente com qualquer bônus que o jogador já tenha (Arremesso de
Espada etc.), sem precisar de nenhum código novo em
`PlayerStatsService`. E dobra o dano ao acertar um golpe vindo de trás da
direção que o alvo está olhando (jogador ou mob, checagem puramente
horizontal via produto escalar entre a direção do alvo e o vetor
atacante→alvo). A Espada Longa do Rei Demônio usa a mesma attribute pro
lado oposto: +2 em vez de -1, já que é uma espada longa, não uma adaga.

Toda arma lendária é criada uma única vez, com todo atributo (Ataque,
Velocidade de Ataque igual à penalidade padrão de espada, Agilidade,
alcance) já embutido via `AttributeModifier` escopado à mão principal -
diferente de `SwordDamageService`, não precisa de nenhuma passagem
periódica de "refresh", porque nada nelas muda com o nível/skill de quem
segura (as únicas partes dinâmicas - Two as One e Fúria de Kamish - são
calculadas na hora do golpe, não gravadas na lore). São `Unbreakable` e
ganham brilho de encantamento se Tier S.

## Tier no lugar de Raridade, e lore mais curta

Versão inicial usava um `Rarity` próprio ("Rarity: S-Rank" etc.) e uma
lore longa (descrição em prosa de quem usou a arma, parágrafos separados
por linhas em branco) - destoava do resto do jogo, que usa o sistema de
`ItemTier` (`TIER S`/`TIER A`/... colorido, igual em toda arma/ferramenta/
armadura) e lore enxuta (só linhas de stat, sem parágrafo narrativo).

`Rarity` foi removido; `LegendaryWeapon` agora carrega um `ItemTier` de
verdade, fixado no item via `ItemTierService#forceTier` - o mesmo truque
que `BuilderWandService` já usava pra pinar seu Stick em Tier S. A cor do
nome e a linha "TIER X SWORD" no fim da lore vêm de
`ItemTierService#applyTier` (extraído do antigo método privado `tooltip`,
agora público) chamado direto em `LegendaryWeaponService#create` - não
precisa esperar o próximo tick da varredura de inventário pra aparecer
certo, nem no preview dentro do menu `/rpgitems`. Por isso a checagem
`LegendaryWeaponService.isLegendary(item)` saiu do `ItemTierService`: ele
processa armas lendárias normalmente agora, só que com o Tier fixado em
vez de derivado do material `_SWORD` por baixo. `SwordDamageService` e
`SwordThrowListener` continuam com a checagem (eles genuinamente
calculariam Ataque/Velocidade errados ou disparariam Arremesso de Espada
em cima da habilidade própria da Espada Longa do Rei Demônio).

A lore em si perdeu toda a prosa: cada arma agora só lista Tipo, Ataque,
Agilidade (se tiver) e uma única linha nomeando seu efeito especial já
com o número - "Two as One: +0.5 dano/Strength", "+25% de dano contra
blindados", "Storm of White Flames: F, 40 Mana, 30s" etc. - antes esse
ganho de Strength só aparecia como texto solto ("baseado no Strength do
usuário"), sem o valor real.

## Agilidade é pra Velocidade o que Inteligência é pra Mana

Antes, o +10 de Agilidade da Adaga de Baruka só existia escondido dentro
de um `AttributeModifier` no próprio item - não tinha nenhum número
"Agilidade" visível em lugar nenhum, diferente de Inteligência (que já
aparecia como stat própria mesmo alimentando o Mana Máximo por trás).

Agora Agilidade segue exatamente o mesmo padrão: `PlayerStatsService`
ganhou `baseAgility()` (config `stats.base-agility`, default 0, igual a
`baseIntelligence()`) e `effectiveAgility(Player)` (base + o que a arma
atualmente equipada concede - `LegendaryWeaponService#heldAgilityBonus`,
ligado via um `stats.legendary(LegendaryWeaponService)` setter pós-
construção, mesmo padrão de `general`/`abilities`/`global`). Esse número
aparece tanto no resumo rápido (`SkillsMenuService#head`, a cabeça no
menu de Habilidades) quanto na aba "Status de Combate" detalhada, com
linha de fonte igual à de Inteligência ("Base X" + "Arma equipada +Y").

A relação com Velocidade também virou 1-por-1: cada ponto de Agilidade
agora soma exatamente +1% de Velocidade (era +2% antes da correção do
fator de conversão em `LegendaryWeaponService`), o mesmo tipo de
proporção limpa que 1 Inteligência = +1 Mana Máximo já tinha. A aba de
Status de Combate ganhou uma linha de Velocidade nova (não existia lá
antes, só no resumo rápido) mostrando "Base 100%" + "Agilidade +X%".
Velocidade continua lendo o atributo vanilla de verdade (não um número
puramente calculado como Mana), então continua refletindo também
qualquer outra fonte (poções etc.) além da Agilidade.

## Ajustes na Espada Longa do Rei Demônio, Storm of White Flames e Adaga de Baruka

Três correções pontuais nas armas lendárias:

- **Espada Longa do Rei Demônio agora tem +2 de alcance de ataque** em vez
  de nenhum bônus/penalidade - fazia sentido uma espada longa alcançar
  mais que uma espada comum, não menos. `LegendaryWeaponService#create`
  generalizou o cálculo de alcance: Adaga usa -1 (0 pra Fúria de Kamish,
  isenta), Espada Longa usa +2, ambos no mesmo `AttributeModifier`
  escopado à mão principal que já existia.
- **Storm of White Flames mira em inimigos de verdade, não em pontos
  aleatórios.** Antes, cada um dos 6 raios caía num ponto aleatório
  dentro de um raio de 4 blocos do local mirado, e só então procurava
  entidades perto daquele ponto aleatório - na prática, raios podiam cair
  onde não tinha nada. Agora `DemonKingStormListener#nearbyTargets`
  primeiro encontra todo `LivingEntity` na área (as 4 blocos de raio de
  busca), ordena pelos mais próximos do ponto mirado, e cada raio cai
  direto em cima de um alvo real (até 6). Sem nenhum alvo na área, cai um
  raio só de efeito cosmético no ponto mirado, como feedback de que a
  habilidade ativou.
- **Adaga de Baruka: Agilidade +10 → +50** (também +50% de Velocidade
  enquanto empunhada, pela mesma proporção 1 Agilidade = +1% Velocidade).

## Agilidade da Adaga de Baruka também vale na off-hand

O `AttributeModifier` de Velocidade de Movimento estava escopado a
`EquipmentSlotGroup.MAINHAND` - só valia se a adaga fosse a arma
empunhada de verdade. Diferente de Ataque/Velocidade de Ataque/alcance
(que só fazem sentido pra arma sendo de fato usada pra golpear), o bônus
de Agilidade é uma velocidade de movimento passiva, então faz sentido
valer mesmo com a adaga guardada na off-hand enquanto luta com outra
arma na mão principal - trocado pra `EquipmentSlotGroup.HAND` (cobre as
duas mãos). `LegendaryWeaponService#heldAgilityBonus` (o número mostrado
na aba de Status) agora soma a Agilidade de ambas as mãos pelo mesmo
motivo, em vez de checar só a mão principal.

## Correção: vidros empilhando também em GUIs de outros plugins (ex.: IcarusChests)

A correção anterior do `FoodTooltipListener` (vidros do menu de Skills
empilhando) checava `topInventory.getHolder() != null` pra distinguir um
inventário real (baú) de um menu virtual do plugin - mas isso não cobre
todo caso: um GUI de OUTRO plugin (ex.: `IcarusChests`, que abre suas
telas de armazenamento via `Bukkit.createInventory(new
IcarusChestHolder(...), ...)`) tem um holder não-nulo próprio, só que
continua sendo tão virtual quanto os nossos - não está preso a bloco
nenhum, só usa esse holder pra identificação interna dos próprios
cliques. Resultado: os vidros decorativos daquele GUI também empilhavam,
do mesmo jeito que os nossos empilhavam antes da primeira correção.

Trocado `getHolder() != null` por `getLocation() != null`: só é
não-nulo pra um inventário de verdade preso a um bloco físico (baú, baú
de ender, barril...), nunca pra um `Bukkit.createInventory(...)`
sintético, seja o holder dele `null` ou uma classe própria do plugin que
o criou. Cobre tanto os nossos menus quanto o de qualquer outro plugin
que use esse padrão comum de GUI.

## Two as One / Fúria de Kamish mostram o bônus de Strength ao vivo

Antes a lore só mostrava a taxa fixa ("Two as One: +0,5 dano/Strength"),
igual pra qualquer jogador - não o quanto aquilo realmente vale pra quem
tá segurando. Agora mostra os dois: `LegendaryWeaponService#create`
grava em qual linha da lore fica esse texto (`STRENGTH_LINE_KEY`, um
índice guardado no PDC do item) e `refreshStrengthLore(Player)` -
chamado no mesmo laço periódico por jogador que já roda
`SwordDamageService#applySwordDamage` - reescreve só aquela linha com o
bônus real de quem está segurando ("Two as One: +25 (0,5/Strength)"),
sem tocar em nenhuma outra parte da lore. Atualiza sozinho conforme o
Strength do jogador muda (Nível Global, Coleta...), do mesmo jeito que a
linha de Velocidade de Ataque das espadas comuns já se mantém atual com
o nível de Combate.

## Paralisia e Sangramento da Presa de Kasaka sempre juntos, 30% de chance

Eram dois rolls independentes de 25% cada - dava pra proc só um dos dois
efeitos no mesmo golpe, ou nenhum, ou os dois. Agora é um único roll de
30% (`PROC_CHANCE` em `LegendaryWeaponService`) que aplica os dois
efeitos juntos sempre que acontece - nunca só um. A lore também virou
uma linha só ("Paralisia + Sangramento: 30% de chance") em vez de duas.

## Dano customizado também pra machado/picareta/pá/enxada, não só espada

`SwordDamageService` só cobria espadas; ferramentas continuavam batendo
com o dano vanilla (irrisório perto da vida na casa das centenas deste
RPG). Novo `ToolDamageService` + `ToolDamageListener` - mesmo padrão
exato de `SwordDamageService`/`SwordDamageListener` (attribute modifier
de Ataque/Velocidade escopado à mão principal, lore com as duas linhas,
reaplicado no join e a cada tick do HUD) - agora dá um dano fixo por
família de material a machados, picaretas, pás e enxadas igualmente (um
Machado, Picareta, Pá e Enxada de Diamante todos batem 30):

```
Madeira / Ouro -> 10
Pedra          -> 15
Cobre          -> 20
Ferro          -> 25
Diamante       -> 30
Netherite      -> 35
```

Reaproveita `SwordDamageService#ATTACK_SPEED_DELTA` pra Velocidade de
Ataque em vez do valor vanilla (bem inconsistente) de cada ferramenta -
como o `attribute_modifiers` já é sobrescrito por completo de qualquer
jeito, toda ferramenta corpo-a-corpo do plugin acaba batendo na mesma
fórmula base de velocidade, diferenciada só pelo Dano de Ataque e pelo
Nível de Combate de quem segura, igual às espadas já faziam.

## Biome's Wand (`/biomewand`)

Nova ferramenta admin-only, mesmo status de sempre (só via comando,
sem craft nem drop) e mesmo esquema de controles do Builder's Wand:
clique esquerdo (na verdade o swing, não o `PlayerInteractEvent` de
clique no ar - ver o doc de `BuilderWandListener#swing` pro motivo)
abre um menu com os biomas disponíveis pra escolher e um controle de
raio; clique direito num bloco pinta o bioma escolhido; Shift + clique
esquerdo desfaz a última pintura. `BiomeWandService`/`BiomeWandListener`
(pacote `dev.icaro.foodtooltips.biome`) espelham a estrutura de
`BuilderWandService`/`BuilderWandListener` quase 1:1.

`BiomeOption` cataloga 15 biomas de Overworld com grama/folhagem
visualmente distinta (Planície, Floresta, Floresta de Bétulas, Floresta
Sombria, Pântano, Pântano de Mangue, Selva, Savana, Montanhas, Taiga,
Taiga Antiga, Terras Áridas, Bosque de Cerejeiras, Prado) - uma lista
curada, não todo bioma que tecnicamente tem grama.

O raio (0 a 10 blocos, ajustável no menu, teto configurável em
`config.yml` -> `biome-wand.max-radius`) pinta uma área **quadrada**
em volta do bloco clicado, não circular - de propósito: bioma é
guardado em células de 4x4x4 (`World#setBiome` aplica na célula
inteira que contém a coordenada dada, não só naquele bloco), então um
raio em blocos nunca bate certinho com a borda de um círculo nessa
grade de qualquer jeito. `BiomeWandService#paint` arredonda o raio pra
cima até a célula cheia mais próxima (garante que todo bloco pedido
fique coberto, em vez de parar um pouco antes da borda) e sempre inclui
a célula clicada mesmo em raio 0. A pintura cobre a altura inteira do
mundo naquela área (conceito de bioma é 2D/horizontal, não uma esfera
3D - mesma lógica do brush `//biome` do WorldEdit). Cada raio afetado é
reenviado uma vez via `World#refreshChunk` no fim, pra cor da grama
mudar na hora, sem precisar relogar.

Desfazer guarda só a última pintura (mesmo "desfaz só a última ação"
do Builder's Wand) - uma lista das células realmente alteradas e qual
bioma cada uma tinha antes, restaurada célula por célula.

A Builder's Wand agora usa Blaze Rod como item (era Stick) e a Biome's
Wand usa Stick (era Grass Block) - troca puramente cosmética, cada
wand já se identifica por uma tag PDC própria, não pelo Material.

## Mobs customizados da ilha de combate (`dev.icaro.foodtooltips.island`)

A primeira versão desse sistema usava Citizens2 + Sentinel (NPCs
`EntityType.PLAYER` com skin de jogador de verdade, estilo Hypixel
SkyBlock) - foi abandonada depois de vários problemas (skin caindo pro
padrão Steve por causa da ordem de chamada, NPCs órfãos persistidos
pelo próprio Citizens sobrevivendo a reinícios do servidor mesmo
depois do código parar de criá-los, e o fato de um NPC `PLAYER`
continuar sendo `instanceof Player` pro Bukkit - dano real contra ele
era tratado como PvP). A versão atual usa só mobs vanilla mesmo
(Zumbi, Esqueleto...) com equipamento customizado - mais simples,
mais robusta, e sem nenhuma dependência externa. Citizens2/Sentinel
não são mais usados por nada no plugin (mas `FoodTooltipsPlugin`
ainda limpa, uma vez no `onEnable`, qualquer NPC órfão chamado
"Sentinela da Ilha" que sobrou de servidores que rodaram aquela
versão antiga).

**`IslandMobDefinition`** descreve um tipo de mob inteiramente via
config (`config.yml` -> `island-mobs.mobs.<id>`) - `entity-type`
(qualquer `EntityType` vanilla), vida, dano, multiplicador de
velocidade, arma (`weapon`, um `Material` simples, ou
`legendary-weapon`, o nome de uma `LegendaryWeapon` real - nesse caso
o mob segura uma cópia de verdade do item, tags e tudo), se usa
armadura de ferro completa (`armored`), textura de cabeça customizada
(`head-texture`, opcional) e a chance de dropar sua `legendary-weapon`
ao morrer (`drop-chance-percent`). Adicionar um mob novo não pede
nenhuma mudança de código, só uma entrada nova aqui. Todo mob usa
algum capacete (um de ferro liso se não tiver `head-texture`) - não é
estético, é o que impede um morto-vivo vanilla de pegar fogo durante
o dia.

**A zona é o próprio bioma, não coordenadas**: `IslandMobZone` guarda
o bioma "Cemitério Sombrio" (resolvido pela mesma `BiomeOption` que a
Biome's Wand usa, via `Registry.BIOME`) em vez de um retângulo X/Z -
`contains(Location)` checa se o bloco ali é de fato aquele bioma, então
a população acompanha automaticamente onde a área foi pintada de
verdade (encolhe/cresce junto, sem precisar reconfigurar nada).
`island-mobs.min-x/max-x/min-z/max-z` no config viraram só uma "área de
busca" (onde procurar o bioma), não mais um limite rígido -
`IslandMobService` varre essa área em passos de 4 blocos (mesma célula
de bioma da wand), separa as colunas que realmente têm o bioma, e só
então distribui os spawns de cada `IslandMobDefinition` entre elas
(embaralhadas). A altura de cada spawn usa
`HeightMap.MOTION_BLOCKING_NO_LEAVES` (não a busca padrão, que conta
copa de árvore como "chão" e enterraria o mob nela), e como cada ponto
já vem confirmado como parte do bioma pintado, nunca cai fora do
contorno real da ilha flutuante.

`IslandMobListener` cancela qualquer `CreatureSpawnEvent` cuja razão
não seja `CUSTOM` dentro dessa zona (então só os mobs deste sistema
aparecem ali - `World#spawn(...)`, usado por `IslandMobService`, gera
razão `CUSTOM`, então nunca se autocancela) e, na morte de um mob seu
(identificado pela tag PDC de variante do Bestiário), agenda um
respawn no mesmo ponto (`respawn-ticks`) e rola a chance de drop da
`legendary-weapon` configurada - registrado em prioridade `MONITOR`,
depois do `CombatListener` (que roda na mesma prioridade e foi
registrado primeiro - Bukkit preserva ordem de registro dentro da
mesma prioridade), pra um drop raro desses nunca ser duplicado pelo
bônus de loot do Bestiário. Comando `/islandmobs` (admin) reinicia a
população na hora, sem precisar reiniciar o servidor.

Bestiário com entrada própria por variante: `BestiaryEntry` tem um
campo `id` (a chave real de progresso/PDC - pra um mob vanilla, sempre
`type.key().value()`) e um `customName` opcional; um mob "variante"
recebe um `id` próprio, então `BestiaryCatalog.find(Entity)` (que
checa a tag PDC primeiro) nunca confunde matar um mob customizado com
matar um vanilla comum do mesmo `EntityType`, nem com PvP de verdade.
O ícone de menu de uma entrada variante nunca usa o ovo-de-spawn
automático (só entradas vanilla "canônicas" ganham essa conveniência) -
`Dealt` mostra a própria cabeça customizada configurada
(`island-mobs.mobs.dealt.head-texture`), `Espectro Ossudo` mostra uma
cabeça de esqueleto (`Material.SKELETON_SKULL`).

Mobs atuais:
- **Dealt** (Zumbi) - 200 de vida, 12 de dano, +20% de velocidade,
  armadura de ferro completa, cabeça customizada, Undead's Sword na mão.
- **Espectro Ossudo** (Esqueleto) - mesmos atributos, sem armadura, só
  a Undead's Sword na mão - sem arco no inventário, então a própria IA
  vanilla do esqueleto (só ativa o comportamento de arco quando ele
  está segurando um de verdade) nunca entra em modo à distância e ele
  luta só corpo a corpo.

Os dois têm 2.5% de chance (rolagem independente cada) de dropar uma
Undead's Sword ao morrer, além de valer normalmente XP de Combate,
moedas e Pontos de Sangue como qualquer mob hostil de verdade (ambos
são `instanceof Enemy`, então seguem o mesmo caminho de recompensa sem
nenhum guard especial).

## Undead's Sword (`/rpgitems`)

Sétima Arma Lendária, e a primeira de um tipo novo: `WeaponType.SWORD`
(ao lado de `DAGGER` e `LONGSWORD`) - sem penalidade/bônus de alcance
e sem dobro de dano por trás, já que a identidade inteira dessa arma é
um bônus situacional, não um gimmick de posicionamento. Representada
por uma Espada de Ferro comum (Tier C), mesmo Dano de Ataque de uma
espada de ferro normal (+30, igual `SwordDamageService`), mas
`LegendaryWeaponService#undeadMultiplier` dobra o dano final (+100%)
quando o alvo é um dos `UNDEAD_TYPES` (Zumbi, Esqueleto, Afogado,
Wither Skeleton, Phantom... o mesmo grupo que Smite e poções de
Dano/Cura Instantânea reconhecem como morto-vivo) - multiplicador a
mais na mesma cadeia que já tinha backstab/blindado, sem efeito ativo
próprio.

Além do menu `/rpgitems`, também dropa dos mobs da ilha de combate
(Dealt e Espectro Ossudo, 2.5% cada) - a primeira Arma Lendária a ter
uma segunda fonte de obtenção além do menu admin.

## Chance de crítico: fim do crit por pulo, base fixa de 20%

O crítico vanilla "pula e ataca no ar" (`CombatListener#damage`'s
antigo `vanillaCritical`) foi removido por completo - crítico agora só
acontece pela rolagem de porcentagem do próprio sistema de Combate,
nunca automaticamente por estar caindo.

`CombatSkillService#critChance(level)` ganhou uma base fixa,
`combat.base-crit-chance` (padrão 20.0) no `config.yml`, somada ao
scaling por nível de sempre (`combat.crit-chance-per-level`) - ou seja,
todo jogador começa com 20% de chance de crítico mesmo no Nível de
Combate 0, sem precisar pular.

Como a base passou a somar com o scaling por nível e com o bônus da
árvore de habilidades (`Ruthless Strikes`), o total já conseguia passar
de 100% antes mesmo dessa mudança (Nível 200 × 0.5%/nível = 100%, mais
até +10% do nó no rank máximo = 110%) - por isso todo lugar que soma
esses dois números agora passa o resultado por `Math.min(100.0, ...)`:
a rolagem de dano em `CombatListener#damage`, e as duas exibições de
Chance de Crítico no menu `/skills` (`SkillsMenuService#head` e
`#combatStatsItem`). Nunca vai aparecer, nem valer na prática, mais que
100% de chance de crítico.

## Menu de Locais (`/skills` → Locais)

`TravelMenuService` (`dev.icaro.foodtooltips.travel`) é um novo botão
no menu principal do `/skills` (ícone de Ender Pearl, só aparece
depois de injetado no `FoodTooltipsPlugin`) que abre uma lista de
destinos de teleporte - gratuito e ilimitado, sem precisar de nenhum
item. Nomes de mundo nunca aparecem pro jogador (`combat_island`
sempre é mostrado como "Ilha de Combate"/"Combat Island"):

- **Mundo Padrão** - spawn do mundo configurado em
  `travel.default-world` (padrão `world`).
- **Ilha de Combate** - spawn de `island-mobs.world` (o mesmo mundo
  dos mobs da ilha), exige Nível de Combate mínimo configurado em
  `travel.combat-island-min-level` (padrão 5, baseado em testes reais:
  com equipamento de ferro completo já dá pra lidar com uns 2 mobs da
  ilha ao mesmo tempo nesse nível). Abaixo do nível exigido, a opção
  aparece bloqueada (tingida de cinza, com a exigência na lore) em vez
  de simplesmente sumir.

Ao alcançar o Nível de Combate mínimo, a mensagem de level-up do
Combate ganha uma linha extra avisando que a Ilha de Combate foi
desbloqueada, e o próprio nó daquele nível na tela de progressão de
Combate (`/skills` → Combate) mostra "Desbloqueia: Ilha de Combate" na
lore, do mesmo jeito que outras skills já marcam seus desbloqueios
(ex: Vein Miner na Mineração).

Substitui por completo o antigo Bilhete da Ilha de Combate
(`IslandAccessService`/`IslandAccessListener`, removidos) - ficou
obsoleto assim que o teleporte passou a ser de graça pelo menu, sem
precisar carregar nem gastar um item.

## Proteção de blocos na ilha de combate

`IslandProtectionListener` cancela `BlockBreakEvent`/`BlockPlaceEvent`
pra qualquer jogador que não esteja em modo Criativo enquanto estiver
em `island-mobs.world` (mesmo mundo do `combat_island`) - protege a
construção do cemitério contra grief/farm por jogadores em modo
sobrevivência. OP sempre ignora a restrição (ajustes rápidos de admin
sem precisar trocar de gamemode). Liga/desliga em
`island-mobs.protect-blocks` (padrão `true`).

## Nome dos mobs da ilha por idioma do cliente

O nome flutuante dos mobs da ilha (ex: "Espectro Ossudo"/"Bony
Specter") agora varia por jogador de acordo com o idioma do cliente
dele, igual toda mensagem/menu do resto do plugin - dois jogadores
olhando pro mesmo mob simultaneamente podem ver nomes diferentes.

Isso não é possível com o nametag vanilla nem com o texto genérico
que `MobVisualService` já usa pra HP (`[Lv X] 200/200❤`) - ambos são
um único valor transmitido igual pra todo mundo; só uma chave de
tradução real do Minecraft (`Component.translatable`, como a espécie
do mob) é resolvida no cliente de cada um, e "Espectro Ossudo" não é
uma dessas chaves. Por isso `IslandMobService` não seta mais
`customName` nenhum - `MobVisualService#setLocalizedName` cria dois
`TextDisplay` (um PT, um EN) acima do mob, e a cada mob deixa visível
só o que bate com `Language.of(viewer)` de cada jogador ao redor,
escondendo o outro - a mesma técnica de mostrar/esconder por jogador
que já existia pra decidir quando a barra de vida aparece por
distância.

Cada mob configura os dois nomes em `island-mobs.mobs.<id>`:
`display-name` (português) e `display-name-en` (inglês, cai de volta
pro `display-name` se omitido).
