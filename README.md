# IcarusRPG

Plugin de RPG para servidores Paper/Spigot (`dev.icaro.foodtooltips.FoodTooltipsPlugin`,
registrado como `IcarusRPG` no `plugin.yml`) — sistemas de progressão inspirados no
Hypixel SkyBlock: nível de Combate com árvore de habilidades, skills gerais com bônus
de atributo, raridade por Tiers, Armas Lendárias e Bestiário com milestones.

> **Renomeado de NexusRPG pra IcarusRPG na v0.39.0.** O pacote Java
> (`dev.icaro.foodtooltips`) não mudou, mas o `name:` do `plugin.yml` sim — e é esse
> campo que o Bukkit usa pra nomear a pasta de dados do plugin. Ao atualizar um
> servidor existente, mova `plugins/NexusRPG/` pra `plugins/IcarusRPG/` antes de subir
> o jar novo, ou ele sobe com uma pasta vazia e perde config/dados salvos.

## Build

```
mvn package
```

Gera `target/IcarusRPG-<versão>.jar`. Requer acesso ao repositório da PaperMC
(`repo.papermc.io`) e, para o hook de WorldGuard, ao da EngineHub (`maven.enginehub.org`).
O build de verdade roda no GitHub Actions (`.github/workflows/build.yml`) a cada push —
aba **Actions** → run mais recente → **Artifacts**.

**Versionamento**: toda mudança publicada sobe a versão em `pom.xml` (`<version>`) e
`plugin.yml` (`version:`) — os dois precisam bater. Patch (`0.26.1` → `0.26.2`):
correções, ajustes de config/texto. Minor (`0.26.2` → `0.27.0`, zera o patch): feature
nova ou mudança de sistema.

## Módulos

| Pacote | O que faz |
|---|---|
| `bestiary` | Catálogo de mobs, categorias/abas e milestones de progresso |
| `biome` | Biome's Wand |
| `builder` | Builder's Wand |
| `combat` | Listener de combate, visuais de mob (HP/nome acima da cabeça) |
| `destroyer` | Destroyer's Hand |
| `enchant` | Mesa de Encantamento reformulada (catálogo próprio, Bookshelf Power, Amolador) |
| `food` | Tooltips de comida |
| `global` | Nível Global, XP, cores de badge/tema |
| `i18n` | Idioma por jogador (PT/EN, a partir do locale do cliente) |
| `item` | Tiers de raridade, dano de espada/ferramenta, durabilidade, Armas Lendárias |
| `mining` | Baú do tesouro, gemas, menu de mineração |
| `placeholder` | Expansão de PlaceholderAPI (opcional) expondo stats do plugin pra outros plugins |
| `protect` | Hooks de proteção (WorldGuard/GriefPrevention) |
| `skills` | Skills de combate/gerais, árvore de habilidades, Estrela do Menu |
| `stats` | Status do jogador e HUD |
| `travel` | Menu de Locais (teleporte rápido) |
| `citizens` | Soft-dependency bridge pra Citizens2/Sentinel — hoje só usada por `CombatListener` pra reconhecer um NPC como abate válido (`isNpc`); a limpeza de NPCs de uma implementação antiga (não deste pacote) fica em `FoodTooltipsPlugin`, uma migração de uma vez só, não uma limpeza geral de órfãos |

Não existe mais economia de moedas nem Ilha de Combate — removidas (a moeda nunca teve
sumidouro nenhum, só subia; a ilha some numa reconstrução de mapa do zero, num mundo
novo).

Os pacotes `shop` (loja/portais) e as mochilas extras (`BackpackService` e afins, em
`skills`) foram removidos — armazenamento vira um plugin próprio, separado.

## Combate & Progressão

- **Nível de Combate** (0-200): dano (+4%/nível), Velocidade de Ataque (escala até o
  nível 50, depois estabiliza) e Chance Crítica — **base de 20%** pra todo jogador
  (`combat.base-crit-chance`) + 0.5%/nível + bônus de habilidade, sempre limitada a
  100% no total. Não existe mais crítico por pulo (jump crit) — só a rolagem de
  porcentagem conta.
- **Árvore de Habilidades de Combate** (`/skills` → Árvore de Combate): 7 habilidades
  em 2 ramos de 3 nós cada — Fúria (Golpes Implacáveis → Berserker → Maestria Crítica)
  e Sangue (Sede de Sangue → Colheita de Almas → Segundo Fôlego) — convergindo no único
  nó de Precisão, Arremesso de Espada (ativo por keybind, exige o topo dos dois ramos).
  Custa **Pontos de Sangue** 🩸 (ganhos por abate/level-up), com Nível de Combate
  mínimo por tier além do custo. Botão de reset devolve os pontos gastos.
- **Nível Global**: XP linear sem teto real, dá +HP e +Strength por faixa de nível, e
  desbloqueia **Telecinese** (loot direto pro inventário) num nível configurável.
- **Skills gerais** (Mineração, Agricultura, Pesca, Coleta, Encantamento, Alquimia):
  cada uma dá Fortune e/ou um bônus de atributo por nível (Vida, Strength,
  Inteligência ou Defesa, dependendo da skill) — ver `/skills` → skill individual.
  Todas as skills (Combate incluído) compartilham a mesma curva de XP por nível
  (`SkillXpCurve`): tabela explícita para os níveis 1-30, fixa em 1.000.000 de XP a
  partir do 31. A barra de progresso (boss bar) de cada skill tem uma cor própria:
  Agricultura verde, Pesca azul, Mineração branca, Coleta amarela, Encantamento rosa,
  Alquimia roxa (Combate mantém a vermelha).
- **Menu de Locais** (`/skills` → Locais): teleporte grátis e ilimitado pro Mundo
  Padrão.

## Vida, Defesa e Dano

| | Valor |
|---|---|
| HP base do jogador | 100 (`stats.base-health`) |
| Vida máxima dos mobs | ×5 (`mob-visuals.health-multiplier`) |
| Defesa | Só do equipamento (vanilla zerado) — mesma fórmula de mitigação `defesa/(defesa+100)` |

**Defesa por peça de armadura:**

| Material | Capacete | Peitoral | Calça | Bota | Total |
|---|---|---|---|---|---|
| Couro | 5 | 15 | 10 | 5 | 35 |
| Cobre | 6 | 18 | 13 | 6 | 43 |
| Corrente | 9 | 23 | 18 | 8 | 58 |
| Ouro | 10 | 25 | 15 | 5 | 55 |
| Ferro | 12 | 30 | 25 | 10 | 77 |
| Diamante | 15 | 40 | 30 | 15 | 100 |
| Netherite | 18 | 46 | 35 | 18 | 117 |

O encantamento Protection soma +4 de Defesa por nível (até nível V), por peça,
empilhando em cima da tabela acima.

**Dano corpo a corpo por material** (espada, machado, picareta, pá e enxada batem
igual dentro do mesmo material):

| Material | Dano |
|---|---|
| Madeira / Ouro | 20 (ferramentas: 10) |
| Pedra / Cobre | 25 (ferramentas: 15/20) |
| Ferro | 30 (ferramentas: 25) |
| Diamante | 35 (ferramentas: 30) |
| Netherite | 40 (ferramentas: 35) |

**Lança (todos os tiers), Tridente e Maça** multiplicam o dano vanilla por 5:

| Item | Dano |
|---|---|
| Lança Madeira/Ouro | 25 |
| Lança Pedra/Cobre | 30 |
| Lança Ferro | 35 |
| Lança Diamante | 40 |
| Lança Netherite | 45 |
| Tridente | 45 |
| Maça | 30 |

Sharpness/Smite/Bane of Arthropods aplicam a % descrita de verdade (5%/nível, 30% no
nível V) sobre o dano-base limpo da arma, em vez do bônus nativo (pequeno, fixo) do
vanilla — só em golpe corpo a corpo, nunca em flecha.

A tooltip do item mostra Dano de Ataque e **Velocidade de Ataque real** (recalculada
com o Nível de Combate do jogador, não o valor cru do modificador).

**Dano de queda** é multiplicado por 5, pra qualquer entidade (jogador ou mob) —
Feather Falling continua reduzindo por cima desse valor já multiplicado.

## Itens & Raridade

Todo item tem uma raridade (`ItemTier`): `S` (dourado) > `A` (roxo) > `B` (azul) >
`C` (verde) > `D` (branco, padrão) > `E` (cinza, blocos/itens crus). Resolvido por
família de material, com overrides por `Material` em `item-tiers:` no `config.yml`
ou fixado por item específico via `ItemTierService#forceTier` (usado pelas
ferramentas/armas únicas do plugin). Mostrado como `TIER {letra}` no nome/lore do
item, aplicado no join e reaplicado a cada tick do HUD.

Durabilidade Máxima de todo item danificável é multiplicada por
`items.durability-multiplier` (padrão 5) — exceto itens de ouro (`GOLDEN_*`:
ferramentas, armas, armadura, horse armor), que ganham 1.000 de Durabilidade Máxima
fixa em vez do multiplicador padrão.

### Armas Lendárias (`/rpgitems`, admin-only)

| Arma | Tipo | Tier | Ataque | Efeito |
|---|---|---|---|---|
| Presa de Veneno de Kasaka | Adaga | C | +25 | 30% de chance: Paralisia + Sangramento juntos |
| Matador de Cavaleiros | Adaga | B | +75 | +25% dano vs. alvo com armadura |
| Adaga de Baruka | Adaga | A | +110 | +50 Agilidade (+50% Velocidade, vale nas duas mãos) |
| Adagas do Rei Demônio | Adaga | S | +220 | Two as One: +0,5 dano/Strength |
| Espada Longa do Rei Demônio | Espada Longa | S | +350 | +2 alcance; Storm of White Flames (F, 40 Mana, 30s) |
| Fúria de Kamish | Adaga | S | 1500 + 1/Strength | Sem penalidade de alcance |
| Undead's Sword | Espada | C | +30 | +100% dano vs. mortos-vivos |

Toda Adaga tem -1 de alcance e dobra o dano por trás; Espada Longa e Undead's Sword
não têm gimmick de posicionamento. Todas são `Unbreakable`, ganham brilho se Tier S,
e mostram Ataque/Velocidade de Ataque real na tooltip (a de Velocidade se atualiza
com o Nível de Combate de quem segura).

## Encantamentos

Clicar com o botão direito numa Mesa de Encantamento de verdade abre a tela própria do
plugin (`EnchantMenuService`) em vez da UI aleatória do vanilla — todo encantamento é
escolhido explicitamente (nunca sorteado), tanto os próprios do plugin
(`IcarusEnchant`) quanto os vanilla de verdade, todos misturados num catálogo único.
Escolher uma entrada abre a tela de níveis, mostrando custo/efeito de cada um; aplicar
gasta níveis de XP reais (como uma bigorna), com desconto proporcional ao nível já
aplicado daquela mesma entrada (ex.: já ter o nível 1 de 5 dá 20% de desconto nos
níveis 2-5; ter o nível 3 de 5 dá 60% de desconto nos níveis 4-5). Remover um
encantamento já aplicado é feito numa tela separada, no Amolador (`Grindstone`).

- **Bookshelf Power**: qualquer Estante numa das 16 posições do anel de 5x5 ao redor
  da mesa (2 blocos de distância em X/Z, não só as 4 paredes cardeais + 4 cantos
  diagonais), no mesmo andar da mesa ou 1 acima, conta 1 ponto — sem exigir linha de
  visão livre entre a mesa e a Estante (uma Estante colada na mesa, ou com algo na
  frente dela, ainda conta). Um único anel completo num andar só já bate o teto.
  Alguns encantamentos/níveis exigem um Bookshelf Power mínimo pra aplicar — nível 1
  de qualquer encantamento é sempre livre, escalando linear até o teto no nível
  máximo daquele encantamento; o nível aparece no menu mesmo bloqueado, só com o
  custo em vermelho.
- **XP da skill de Encantamento** usa a fórmula real do vanilla (Mesa de
  Encantamento/Bigorna): `XP = 3,5 × X^1,5`, onde X é a quantidade de níveis de XP
  gastos na aplicação.
- Vários encantamentos vanilla foram convertidos em entradas próprias do plugin com
  efeito e descrição reais (não mais o efeito nativo do vanilla): Flame, Lure,
  Infinite Quiver, Luck of the Sea, Fire Aspect, toda a família Protection
  (Protection/Fire/Blast/Projectile), Respiration, Thorns, Feather Falling. Os que
  continuam sendo encantamentos vanilla reais (Sharpness, Smite, Bane of Arthropods,
  Power, Knockback, Punch, Looting, Sweeping Edge, Efficiency, Fortune...) tiveram a
  descrição corrigida pra bater com o efeito de verdade, e ganharam efeito real quando
  a descrição prometia algo que não existia (ex.: Efficiency agora aplica um bônus
  real de velocidade de mineração; Fortune agora soma na Mining Fortune de verdade).

## Bestiário (`/bestiary`)

Catálogo de mobs organizado em abas por categoria (Animais, Monstros Terrestres,
Aquáticos, Cavernas, Nether, The End). Cada entrada mostra XP de Combate, Pontos de
Sangue, orbes de XP e drops; matar um mob concede milestones que dão bônus de
dano/loot contra aquele tipo específico.

## Mineração

**Mining Speed** é um stat puramente de equipamento (não sobe com a skill de
Mineração): velocidade base da picareta + nível real de Efficiency encantado
(`10 + 20/nível`), mostrado na tooltip da picareta e aplicado de verdade em jogo via
`Attribute.MINING_EFFICIENCY`.

**Insta-mine**: picareta, machado ou pá de Netherite com Efficiency V (vanilla, de
verdade) quebra instantaneamente qualquer bloco com dureza ≤ 6.0 — cobre pedra,
minério, madeira, terra etc. (Deepslate, o mais duro do grupo normal, é 4.5).
Obsidiana, Ancient Debris, Crying Obsidian e Respawn Anchor continuam lentos como
sempre. Depende só do item + encantamento, sem exigir nível de skill; ferramentas de
ouro ficam de fora dessa garantia.

## Ferramentas de Construção (admin-only, sem craft/drop)

| Ferramenta | Comando | Uso |
|---|---|---|
| Builder's Wand | `/builderwand` | Clique direito estende um bloco em linha/coluna; clique esquerdo abre o menu (modo Linha/Face, alcance); Shift+clique esquerdo desfaz |
| Destroyer's Hand | `/destroyerhand` | Espelho da Builder's Wand, pra limpar em vez de construir |
| Biome's Wand | `/biomewand` | Pinta um bioma numa área quadrada ao redor do bloco clicado (raio ajustável) |

## Comandos

| Comando | Descrição | Permissão |
|---|---|---|
| `/skills` | Menu de Habilidades | — |
| `/bestiary` | Bestiário | — |
| `/nivelglobal` (`/globallevel`, `/level`) | Progresso de Nível Global | — |
| `/levelcolor [tema]` | Tema de cor do nível | — |
| `/rpgitems` | Menu de Armas Lendárias | `foodtooltips.admin` |
| `/setskilllevel [player] <skill> <0-200>` | Define nível de uma skill | `foodtooltips.admin` |
| `/globalxp <player> <get\|give\|set\|remove> [amount]` | Administra XP Global | `foodtooltips.admin` |
| `/resetstats <player>` | Reseta todos os status salvos do jogador | `foodtooltips.admin` |
| `/builderwand [player]` | Dá a Builder's Wand | `foodtooltips.admin` |
| `/destroyerhand [player]` | Dá a Destroyer's Hand | `foodtooltips.admin` |
| `/biomewand [player]` | Dá a Biome's Wand | `foodtooltips.admin` |

## Configuração

Tudo em `config.yml`, com comentários inline por chave. Seções principais:
`stats` (bases de Vida/Mana/etc.), `combat` e `combat-tree` (progressão e árvore),
`global-level`, `item-tiers` (overrides de raridade), `builder-wand`/`destroyer-hand`/
`biome-wand` (limites), `travel` (destino do menu de Locais). Novas chaves introduzidas
em updates são mescladas automaticamente num `config.yml` já existente no servidor
(chaves já presentes nunca são sobrescritas).

## Dependências

- **Inventory Framework (IF)** — shadada e relocada pra `dev.icaro.foodtooltips.libs.inventoryframework`,
  usada pelos menus mais novos (`ChestGui`/`StaticPane`/`GuiItem`). Pinada em `0.12.0`
  por um bug de upstream na `0.12.1` que quebra qualquer `ChestGui`.
- **WorldGuard/GriefPrevention** (opcional) — hooks de proteção em `protect`.
- **Multiverse-Core** (opcional, recomendado) — multi-mundo; o menu de Locais funciona
  com qualquer setup de mundos, Multiverse ou não.
- **PlaceholderAPI** (opcional) — se instalado, registra `IcarusPlaceholders`
  (`placeholder`), expondo `%icarusrpg_globallevel%` (Nível Global do jogador) pra
  outros plugins (ex.: TAB, pra ordenar tab list/nametag por Nível Global).
