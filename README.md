# IcarusRPG

Plugin de RPG para servidores Paper que adiciona progressão contínua, combate
personalizado, equipamentos especiais e interfaces próprias ao Minecraft.

O projeto foi desenvolvido para **Minecraft Java 26.2** com **Java 25** e adapta
os textos automaticamente ao idioma do jogador (português ou inglês).

## Principais recursos

- Skills de Combate, Mineração, Agricultura, Pesca, Coleta, Encantamento e
  Alquimia, reunidas em um Nível Global.
- Atributos como Vida, Defesa, Strength, Mana, chance crítica, velocidade de
  ataque e diferentes tipos de Fortune.
- Árvore de Combate com habilidades ativas e passivas.
- Bestiário com categorias, abates, recompensas e milestones.
- Armas lendárias, armaduras, ferramentas especiais e itens craftáveis.
- Mesa de Encantamentos própria, com escolha direta do encantamento e nível.
- Menus integrados para progressão, crafting, mineração, viagem, aljava e itens.
- Criaturas que escalam conforme tipo, dimensão e profundidade.

## Resource pack

O [IcarusTexture](https://github.com/IcaroFranca/IcarusTexture) fornece as
texturas dos equipamentos, armas e interfaces. O plugin funciona sem ele, mas
alguns itens usam a aparência padrão do Minecraft e os menus perdem parte do
acabamento visual.

## Instalação

1. Use um servidor **Paper 26.2** com **Java 25**.
2. Baixe o JAR mais recente em [Actions](https://github.com/IcaroFranca/IcarusRPG/actions).
3. Coloque o arquivo na pasta `plugins/`.
4. Configure o IcarusTexture no `server.properties`, se desejar.
5. Reinicie o servidor por completo.

O `config.yml` é criado na primeira inicialização. Novas opções são incorporadas
nas atualizações sem substituir valores já configurados.

## Comandos

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

Também existem comandos administrativos para entregar ferramentas especiais,
como `/builderwand`, `/destroyerhand` e `/biomewand`.

## Integrações opcionais

- **WorldGuard e GriefPrevention:** proteção de áreas.
- **Citizens e Sentinel:** integração de NPCs com o combate.
- **PlaceholderAPI:** disponibiliza `%icarusrpg_globallevel%`.
- **Geyser/Floodgate:** compatibilidade com jogadores Bedrock.

## Compilação

```bash
mvn package
```

O JAR será criado em `target/`. O GitHub Actions também compila cada atualização
e disponibiliza o artefato correspondente.

Relatos de bugs e sugestões podem ser enviados pela aba
[Issues](https://github.com/IcaroFranca/IcarusRPG/issues).
