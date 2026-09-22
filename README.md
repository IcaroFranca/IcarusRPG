# IcarusRPG

[Português](#português) | [English](#english)

<a id="português"></a>
## Português

Plugin de RPG para servidores Paper que adiciona progressão contínua, combate
personalizado, equipamentos especiais e interfaces próprias ao Minecraft.

Desenvolvido para **Minecraft Java 26.2** com **Java 25**, adapta os textos
automaticamente ao idioma do jogador (português ou inglês).

### Principais recursos

- Skills, Nível Global e atributos personalizados.
- Árvore de Combate com habilidades ativas e passivas.
- Bestiário com categorias, recompensas e milestones.
- Armas lendárias, armaduras e ferramentas especiais.
- Mesa de Encantamentos própria com escolha direta de encantamento e nível.
- Menus de progressão, crafting, mineração, viagem, aljava e itens.
- Criaturas que escalam conforme tipo, dimensão e profundidade.

### Resource pack

O [IcarusTexture](https://github.com/IcaroFranca/IcarusTexture) fornece as
texturas dos equipamentos, armas e interfaces. O plugin funciona sem ele, mas
alguns elementos usam a aparência padrão do Minecraft.

### Instalação

1. Use um servidor **Paper 26.2** com **Java 25**.
2. Baixe o JAR mais recente em [Actions](https://github.com/IcaroFranca/IcarusRPG/actions).
3. Coloque o arquivo na pasta `plugins/`.
4. Configure o IcarusTexture no `server.properties`, se desejar.
5. Reinicie o servidor por completo.

O `config.yml` é criado na primeira inicialização e preserva os valores já
configurados ao receber novas opções.

### Comandos

| Comando | Função |
|---|---|
| `/skills` | Abre o menu principal de progressão |
| `/bestiary` | Abre o Bestiário |
| `/nivelglobal` | Mostra o progresso do Nível Global |
| `/levelcolor` | Escolhe uma cor de nível desbloqueada |
| `/rpgitems` | Abre o catálogo administrativo de itens especiais |
| `/setskilllevel` | Altera o nível de uma skill |
| `/globalxp` | Consulta ou altera XP Global |
| `/resetstats` | Reinicia o progresso salvo de um jogador |

Também existem comandos administrativos para ferramentas especiais, como
`/builderwand`, `/destroyerhand` e `/biomewand`.

### Integrações opcionais

- **WorldGuard e GriefPrevention:** proteção de áreas.
- **Citizens e Sentinel:** integração de NPCs com o combate.
- **PlaceholderAPI:** disponibiliza `%icarusrpg_globallevel%`.
- **Geyser/Floodgate:** compatibilidade com jogadores Bedrock.

### Compilação

```bash
mvn package
```

O JAR será criado em `target/`. O GitHub Actions também disponibiliza um
artefato para cada atualização. Bugs e sugestões podem ser enviados em
[Issues](https://github.com/IcaroFranca/IcarusRPG/issues).

[Voltar ao topo](#icarusrpg)

---

<a id="english"></a>
## English

RPG plugin for Paper servers that adds continuous progression, custom combat,
special equipment, and custom interfaces to Minecraft.

Built for **Minecraft Java 26.2** with **Java 25**, it automatically adapts its
text to each player's language (Portuguese or English).

### Main features

- Skills, Global Level, and custom attributes.
- Combat Tree with active and passive abilities.
- Bestiary with categories, rewards, and milestones.
- Legendary weapons, armor, and special tools.
- Custom Enchanting Table with direct enchantment and level selection.
- Menus for progression, crafting, mining, travel, quiver, and items.
- Creatures that scale according to type, dimension, and depth.

### Resource pack

[IcarusTexture](https://github.com/IcaroFranca/IcarusTexture) provides textures
for equipment, weapons, and interfaces. The plugin works without it, but some
elements will use Minecraft's default appearance.

### Installation

1. Use a **Paper 26.2** server running **Java 25**.
2. Download the latest JAR from [Actions](https://github.com/IcaroFranca/IcarusRPG/actions).
3. Place the file in the `plugins/` directory.
4. Configure IcarusTexture in `server.properties`, if desired.
5. Fully restart the server.

The `config.yml` file is created on first startup. New options are added during
updates without replacing existing values.

### Commands

| Command | Description |
|---|---|
| `/skills` | Opens the main progression menu |
| `/bestiary` | Opens the Bestiary |
| `/nivelglobal` | Shows Global Level progress |
| `/levelcolor` | Selects an unlocked level color |
| `/rpgitems` | Opens the administrative special-item catalog |
| `/setskilllevel` | Changes a skill level |
| `/globalxp` | Views or changes Global XP |
| `/resetstats` | Resets a player's saved progress |

Administrative commands are also available for special tools, including
`/builderwand`, `/destroyerhand`, and `/biomewand`.

### Optional integrations

- **WorldGuard and GriefPrevention:** area protection.
- **Citizens and Sentinel:** NPC combat integration.
- **PlaceholderAPI:** provides `%icarusrpg_globallevel%`.
- **Geyser/Floodgate:** Bedrock player compatibility.

### Building

```bash
mvn package
```

The JAR will be created in `target/`. GitHub Actions also provides an artifact
for each update. Bugs and suggestions can be submitted through
[Issues](https://github.com/IcaroFranca/IcarusRPG/issues).

[Back to top](#icarusrpg)
