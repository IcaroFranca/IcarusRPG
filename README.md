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
- **Aljava** (`/skills`, a partir do Nível de Combate 5): armazenamento próprio de 27
  slots (o mesmo espaço de um baú simples), só aceita flechas (normais, tocadas ou
  espectrais). O arco puxa flechas direto de lá sempre que o inventário normal do
  jogador estiver sem nenhuma — não precisa carregar flecha nenhuma no inventário. Se o
  jogador guardar o arco sem atirar, a flecha emprestada volta pra Aljava em vez de
  ficar acumulada no inventário normal (ela só existe ali de fato no instante do tiro).
  Persiste entre sessões, independente do inventário do jogador.
- **Árvore de Habilidades de Combate** (`/skills` → Árvore de Combate): 7 habilidades
  em 2 ramos de 3 nós cada — Fúria (Golpes Implacáveis → Berserker → Maestria Crítica)
  e Sangue (Sede de Sangue → Colheita de Almas → Segundo Fôlego) — convergindo no único
  nó de Precisão, Arremesso de Espada (ativo por keybind, exige o topo dos dois ramos).
  Custa **Pontos de Sangue** 🩸 (ganhos por abate/level-up), com Nível de Combate
  mínimo por tier além do custo. Botão de reset devolve os pontos gastos.
- **Nível Global**: XP linear sem teto real, dá +HP e +Strength por faixa de nível, e
  desbloqueia **Telecinese** (loot direto pro inventário) num nível configurável, e a
  **teleportação da bússola de morte** no Nível Global 5 (`global-level.death-teleport-level`).
  A bússola em si (aponta pro local da sua última morte) sempre foi dada no respawn,
  independente de nível — abaixo do nível 5 ela continua sendo só um ponteiro; a partir
  dele, clicar nela e clicar de novo em até 10s (pra confirmar) te teleporta pra lá.
- **Cor do Nível** (`/levelcolor`): escolhe um tema de cor entre os desbloqueados pelo
  Nível Global (do branco padrão até temas animados/gradiente como Prismático,
  Netherite, Nexus...) — a cor escolhida tinge o nome do jogador inteiro (não só o
  badge `[N]`), igual na tab list, no chat e no nametag acima da cabeça (esse último
  limitado às 16 cores nomeadas do vanilla, já que o time do scoreboard não aceita RGB
  livre — a cor mais próxima do tema é escolhida automaticamente).
- **Skills gerais** (Mineração, Agricultura, Pesca, Coleta, Encantamento, Alquimia):
  cada uma dá Fortune e/ou um bônus de atributo por nível (Vida, Strength,
  Inteligência ou Defesa, dependendo da skill) — ver `/skills` → skill individual.
  Fortune (Mineração/Agricultura/Coleta) funciona em pontos percentuais: cada ponto é
  1% de chance de dropar o dobro do item coletado; a cada 100 pontos completos essa
  cópia extra vira garantida e o excedente passa a ser a chance da PRÓXIMA cópia (120
  de Fortune = dobro garantido + 20% de chance de sair o triplo, 250 de Fortune =
  triplo garantido + 50% de chance de sair o quádruplo, e assim por diante).
  Todas as skills (Combate incluído) compartilham a mesma curva de XP por nível
  (`SkillXpCurve`): tabela explícita para os níveis 1-30, fixa em 1.000.000 de XP a
  partir do 31. A barra de progresso (boss bar) de cada skill tem uma cor própria:
  Agricultura verde, Pesca azul, Mineração branca, Coleta amarela, Encantamento rosa,
  Alquimia roxa (Combate mantém a vermelha).
- **Menu de Locais** (`/skills` → Locais): teleporte grátis e ilimitado pro Mundo
  Padrão, ou pra sua própria cama/âncora de respawn mais recente (`Player#getRespawnLocation()`
  - cobre tanto cama no Overworld quanto âncora de respawn no Nether). Sem cama/âncora
  marcada, o clique só avisa que não há nenhuma.
- **Habilidades Passivas** (`/skills`, slot 29): liga/desliga habilidades passivas
  individualmente — hoje só a Telecinese, separada em duas chaves independentes (drops
  de mobs e drops de blocos). Continua exigindo o nível de desbloqueio normal
  da Telecinese; a tela só aparece destrancada a partir dele, e por padrão as duas
  ficam ativadas (nada muda pra quem nunca abrir essa tela). O item vai direto pro
  inventário sem nunca chegar a aparecer caído no chão — incluindo as cópias extras
  que a Mining/Farming/Foraging Fortune gera além de uma stack cheia, que antes
  escapavam da Telecinese e ficavam visíveis no chão. Nenhuma das duas chaves depende
  de arma/ferramenta específica (funciona até de mão vazia) nem do tipo de mob/bloco —
  a de mobs vale pra qualquer abate, incluindo por arco e flecha; a de blocos vale pra
  qualquer bloco quebrado, não só minério/tora/colheita rastreados (esses continuam
  sendo os únicos que também recebem cópias extras de Fortune, já que Fortune só faz
  sentido pra um recurso de verdade). Os orbes de XP são a única exceção completa: uma
  vez a Telecinese desbloqueada, XP de abate sempre vai direto pro jogador,
  independente do estado das duas chaves acima (que só afetam item físico/bloco).

## Vida, Defesa e Dano

| | Valor |
|---|---|
| HP base do jogador | 100 (`stats.base-health`) |
| Vida máxima dos mobs | ×5 base (`mob-visuals.health-multiplier`) + escala por dificuldade — ver abaixo |
| Defesa | Só do equipamento (vanilla zerado) — mesma fórmula de mitigação `defesa/(defesa+100)`, vale pra jogador e mob |

**Dificuldade dos mobs** (`MobDifficultyService`): em cima do ×5 base acima (que nunca
diminui — todo mob de superfície continua com a mesma vida de sempre), dois bônus se
somam:

- **Tier**: reaproveita o `combatXp` que cada mob já tem no Bestiário (Zumbi 50 até
  Ender Dragon 2500) — mobs mais difíceis na progressão do Bestiário ficam mais tanques
  e batem mais forte.
- **Profundidade**: quanto mais abaixo do nível de referência de cada dimensão
  (Overworld Y64, Nether Y128, End Y64) o mob nasce, mais forte fica — vale pra
  caverna funda também, não só pro Nether.

O **Nether tem um piso próprio**: nenhum mob de lá (Piglin incluído, mesmo com
`combatXp` baixo) nasce com menos de 4500 HP ou dá menos de 500 de dano por golpe —
configurável em `mob-visuals.min-health-nether`/`min-damage-nether`. Como a vida
máxima do vanilla trava em 1024 independente do que for setado no atributo, o
excedente acima de `mob-visuals.real-health-cap` (1000) fica num "escudo" próprio
(absorve qualquer dano antes da vida real, igual Absorção do vanilla) — a etiqueta de
vida acima do mob sempre mostra o total real, sem essa divisão interna aparecer.
Mobs passivos (Vaca, Porco, Lobo, Abelha, Golem de Ferro, Vilarão, peixes/tartaruga/
golfinho/axolote...) nunca escalam.

**Zombie Miner / Skeleton Miner** (`MinerVariantService`): um Zumbi ou Esqueleto
normal (não Husk/Drowned/Stray/Wither Skeleton/Zombie Villager) que nasce no Overworld
abaixo de Y0 vira essa variante em vez do mob comum — veste um set completo de
**"Miner's Armor"**: cabeça customizada própria no lugar do capacete (só visual) +
peitoral/calça/bota de couro tingido de cinza (também só visual). Cada peça é forçada
a ter a mesma Defesa base do Diamante (`ArmorDefenseService#forceDefense`, ignora o
Material real do item) e vem encantada com Proteção V (do próprio plugin, aplica mesmo
na cabeça) — e tanto a Defesa base quanto o bônus da Proteção são **dobrados** pra
quem estiver com pelo menos uma peça equipada E na camada Y0 pra baixo ("camadas
negativas") — vale pro próprio mob (que só existe lá) e também pra um jogador que
looter e vestir a armadura (`MinerVariantService#minerArmorBonusActive`, checado a
cada tick, não uma tag permanente — sair da camada negativa tira o bônus até voltar).
Toda peça é
`Unbreakable`, e por já ser inquebrável o encantamento Unbreaking nunca aparece como
opção na Mesa de Encantamento pra ela (nem pra qualquer outro item já inquebrável do
plugin). O capacete (a cabeça customizada) é reconhecido como um capacete de verdade
na Mesa de Encantamento — mesmo sendo tecnicamente uma `PLAYER_HEAD` por baixo,
`EnchantService#compatibleEntries` testa contra um Capacete de Diamante genérico só
pra essa peça, então Respiração/Proteção/Crescimento etc. aparecem normalmente nela.
Além da armadura, tem um piso garantido de 300 HP / 180 de dano por golpe
(`miner-variants.below-y`/`min-health`/`min-damage`), por cima do que o
Zumbi/Esqueleto normal já teria pelo tier+profundidade acima. Cada variante tem sua
própria entrada na Bestiary (`zombie_miner`/`skeleton_miner`, separada da entrada do
Zumbi/Esqueleto comum mesmo os dois compartilhando o mesmo `EntityType` por baixo),
com nome e ícone próprios (a mesma cabeça customizada que o mob usa) em vez de cair
no ovo de spawn/nome do Zumbi/Esqueleto comum, e listada na aba **Cavernas**, não
Superfície — progresso, milestones e XP de Combate (24 por abate) contam à parte, e
os dois dropam **40 orbes de XP vanilla** fixos (bem acima do ~5 padrão do
Zumbi/Esqueleto comum), sujeito aos mesmos bônus que qualquer outro mob (ex.: o
encantamento Experience ainda pode dobrar). O **Zombie Miner** ainda tem **2,5% de
chance de dropar a Espada dos Mortos-Vivos** — a primeira forma de conseguir essa
arma lendária sem ser pelo `/rpgitems` (admin-only). Cada peça da própria Miner's
Armor também tem **1% de chance base independente de dropar** (capacete, peitoral,
calça e bota rolados separadamente — de nenhuma a todas as quatro no mesmo abate),
escalada pela mesma fórmula de Looting/Luck (ver seção de Encantamentos), sempre no
**Tier A**, já com a Defesa base de Diamante, Proteção V e Unbreakable de quem a
estava usando (a Defesa dobrada volta a valer normalmente pro jogador que a vestir,
enquanto estiver na camada negativa); o drop de equipamento aleatório do vanilla é
totalmente desativado pra esses dois mobs, então esse drop é a única fonte
independente do `/rpgitems` — que também dá um set completo (4 peças) garantido, sem
depender da sorte do drop.

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

Só armas, ferramentas e armaduras têm raridade (`ItemTier`): `S` (dourado) > `A`
(roxo) > `B` (azul) > `C` (verde) > `D` (branco, padrão) > `E` (cinza). Drops,
minérios e outros itens comuns não recebem tier nenhum. Resolvido por família de
material (`ItemTierService#equipmentTier`), com overrides por `Material` em
`item-tiers:` no `config.yml` (funciona pra qualquer item, equipamento ou não — é
uma escolha explícita do dono do servidor) ou fixado por item específico via
`ItemTierService#forceTier` (usado pelas ferramentas/armas únicas do plugin, ex.
Builder's Wand). Mostrado como `TIER {letra}` no nome/lore do item, aplicado no
join e reaplicado a cada tick do HUD; um item já marcado que deixa de ser elegível
(por exemplo, um override removido do config) tem a tag removida automaticamente
na próxima varredura.

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

O menu tem também um slot separado (topo, fora das linhas de armas) que dá o set
completo de **Miner's Armor** (4 peças) de uma vez, no idioma de quem clicou — as
mesmas peças que um Zombie/Skeleton Miner veste e pode dropar (ver seção acima), só
que garantidas em vez de depender do 1% de chance por peça.

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
  diagonais como antes), no mesmo andar da mesa ou 1 acima, conta 1 ponto — desde que
  o bloco entre a mesa e a Estante (o vão de ar de 1 bloco, igual ao vanilla de
  verdade) esteja livre. O teto é 24 — um anel completo num andar só (16) mais metade
  do anel do outro andar já bate nele. Alguns
  encantamentos/níveis exigem um Bookshelf Power mínimo pra aplicar — nível 1 de
  qualquer encantamento é sempre livre, escalando linear até o teto no nível máximo
  daquele encantamento; o nível aparece no menu mesmo bloqueado, só com o custo em
  vermelho.
- **XP da skill de Encantamento** usa a fórmula real do vanilla (Mesa de
  Encantamento/Bigorna): `XP = 3,5 × X^1,5`, onde X é a quantidade de níveis de XP
  gastos na aplicação.
- Um item que chega encantado do jeito vanilla (baú de loot, drop de mob, pesca,
  comércio com aldeão — qualquer coisa fora da Mesa de Encantamento reformulada) tem
  sua tooltip convertida pro mesmo formato colorido (nome + descrição) que a Mesa já
  usa, em vez de mostrar o texto cinza padrão do vanilla — aplicado ao spawnar no
  mundo, ao abrir o baú, e por uma varredura periódica no inventário do jogador. A
  partir de 6 encantamentos aplicados no mesmo item, o bloco "Encantamentos:" passa a
  listar dois nomes por linha (separados por vírgula) em vez de um só, pra não deixar
  a tooltip gigante.
- **Growth** (qualquer peça de armadura, até nível 5): +15 de Vida Máxima por nível,
  somando em cada peça equipada — igual à Proteção, mas concedendo Vida em vez de
  Defesa.
- Vários encantamentos vanilla foram convertidos em entradas próprias do plugin com
  efeito e descrição reais (não mais o efeito nativo do vanilla): Flame, Lure,
  Infinite Quiver, Luck of the Sea, Fire Aspect, toda a família Protection
  (Protection/Fire/Blast/Projectile — +4/+2/+30/+7 respectivamente por nível, e
  **mutuamente excludentes entre si** na mesma peça, igual a regra real do vanilla),
  Respiration, Thorns, Feather Falling. Os que
  continuam sendo encantamentos vanilla reais (Sharpness, Smite, Bane of Arthropods,
  Power, Knockback, Punch, Looting, Sweeping Edge, Efficiency, Fortune...) tiveram a
  descrição corrigida pra bater com o efeito de verdade, e ganharam efeito real quando
  a descrição prometia algo que não existia (ex.: Efficiency agora aplica um bônus
  real de velocidade de mineração; Fortune agora soma na Mining Fortune de verdade).
- **Looting e Luck** controlam a chance de um mob hostil dropar sua arma e cada peça
  de armadura equipada com a mesma fórmula multiplicativa: `Chance Final = Chance
  Base × (1 + Looting × 0,15) × (1 + Luck × 0,05)`, rolada separadamente pra cada
  peça (arma na mão + capacete/peitoral/calça/bota), então de 0 a 5 itens podem
  dropar da mesma morte. "Chance Base" é a chance de drop que o próprio mob já tinha
  pra aquela peça (8,5% padrão do vanilla pra um mob que nasceu com o item, ou mais
  se ele pegou de um jogador) — exceto peças da Armadura de Minerador, que usam sua
  própria chance base fixa em vez da do vanilla (que fica zerada por design). O drop
  nativo do vanilla pra essas peças é substituído por esse cálculo, então Looting e
  Luck são as únicas fontes reais de chance extra.
- **Família de encantamentos corpo a corpo** (apenas espadas): Critical (+dano crítico), Cubism/Ender Slayer/Impaling
  (+dano contra mobs Cúbicos ⚂, do Fim ⊙ e Aquáticos ⚓ respectivamente — cada um com
  sua própria lista de `EntityType`), Execute (+dano por % de vida faltando no alvo),
  Giant Killer (+dano por % de vida extra que o alvo tiver acima da sua), First Strike
  (+dano no primeiro golpe contra um alvo com vida cheia — libera no nível 10 da skill
  de Encantamento, junto com Execute), Lethality (reduz a Defesa
  do alvo por acerto, empilhando até 4 vezes por 4s), Life Steal (cura % da sua vida
  máxima por acerto), Vampirism (cura % da vida faltante ao matar), Thunderlord (raio
  a cada 3 acertos), Venomous (lentidão + dano contínuo empilhável por acerto),
  Experience (chance de dobrar orbes de XP de mobs ou minérios, também aplicável à
  picareta) e Luck (aumenta a chance de mobs dropar sua arma e armadura equipadas —
  ver fórmula de Looting/Luck acima).
- **Família de encantamentos de agricultura/mineração**: Delicate (machado e enxada —
  impede de quebrar plantações que ainda não cresceram totalmente e caules de abóbora/
  melancia), Harvesting (enxada, até nível 5 — +12,5 de Farming Fortune por nível),
  Replenish (machado e enxada — replanta automaticamente, usando materiais do
  inventário, qualquer plantação quebrada, incluindo cacau e verruga do Nether),
  Smelting Touch (picareta, machado e pá — blocos minerados dropam sua versão
  esquentada pela fornalha; não pode ser combinado com Toque de Seda) e Spawner Touch
  (só picareta — permite quebrar um spawner de mob e recolhê-lo como item preservando
  o mob que ele gera, algo que o vanilla nunca permite mesmo com Toque de Seda;
  recolocar o item devolve o mesmo spawner funcionando igual). Delicate/Harvesting/
  Replenish/Smelting Touch desbloqueiam nos níveis 3/6/9/12 da skill de Encantamento
  respectivamente, Spawner Touch no nível 10 (custa 50 de XP) (Experience continua
  liberado desde o início, como sempre foi).
- **Fortune e Efficiency não aparecem como opção na Mesa de Encantamento para enxada**
  (continuam normais em picareta/machado/pá) — a Fortune vanilla é redundante com a
  Fortune de Agricultura já dada pela skill, e Efficiency não tem efeito relevante numa
  enxada.

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
