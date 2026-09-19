# IcarusRPG

IcarusRPG transforma o Minecraft em uma experiência de progressão contínua, com
skills, atributos, equipamentos especiais, encantamentos próprios e desafios que
ficam mais difíceis conforme o jogador avança.

O projeto foi feito para **Minecraft Java 26.2**, usando Paper, e funciona em
português e inglês de acordo com o idioma do jogador.

## O que o plugin oferece

- **Skills e Nível Global:** evolua Combate, Mineração, Agricultura, Pesca, Coleta,
  Encantamento e Alquimia para melhorar seus atributos.
- **Combate personalizado:** Vida, Defesa, Strength, chance crítica, velocidade de
  ataque e dificuldade de mobs fazem parte da progressão.
- **Árvore de Combate:** desbloqueie habilidades passivas e ativas usando Pontos de
  Sangue obtidos em batalha.
- **Bestiário:** acompanhe criaturas, abates, recompensas e milestones por categoria.
- **Itens especiais:** armas lendárias, armaduras próprias e ferramentas para
  construção, destruição e alteração de biomas.
- **Encantamentos reformulados:** escolha diretamente o encantamento e seu nível,
  sem depender da seleção aleatória do Minecraft.
- **Menus integrados:** crafting, mineração, encantamentos, viagem, aljava e outras
  funções ficam reunidas no menu de Skills.
- **Progressão de mundo:** mobs mais perigosos aparecem conforme o tipo, dimensão e
  profundidade, incluindo Zombie Miner e Skeleton Miner.

## Resource pack

O [IcarusTexture](https://github.com/IcaroFranca/IcarusTexture) faz parte da
experiência e deve ser enviado pelo servidor. Ele contém as texturas dos equipamentos,
armas e interfaces personalizadas.

Sem o pack, o plugin continua funcionando, mas alguns itens usarão a aparência padrão
do Minecraft e os menus perderão o acabamento visual.

## Instalação

1. Use um servidor **Paper 26.2** com **Java 25**.
2. Baixe o JAR mais recente na aba
   [Actions](https://github.com/IcaroFranca/IcarusRPG/actions).
3. Coloque o arquivo em `plugins/`.
4. Configure o IcarusTexture no `server.properties` e torne o pack obrigatório.
5. Reinicie o servidor por completo.

Na primeira inicialização, o plugin cria seu `config.yml`. Novas opções são
adicionadas automaticamente nas atualizações sem apagar os valores já configurados.

## Comandos para jogadores

| Comando | Função |
|---|---|
| `/skills` | Abre o menu principal de progressão |
| `/bestiary` | Abre o Bestiário |
| `/nivelglobal` | Mostra o progresso do Nível Global |
| `/levelcolor` | Escolhe uma cor de nível já desbloqueada |

`/nivelglobal` também aceita os aliases `/globallevel` e `/level`.

## Comandos administrativos

| Comando | Função |
|---|---|
| `/rpgitems` | Abre o catálogo de itens especiais |
| `/setskilllevel [jogador] <skill> <0-200>` | Altera o nível de uma skill |
| `/globalxp <jogador> <get\|give\|set\|remove> [quantidade]` | Consulta ou altera XP Global |
| `/resetstats <jogador>` | Reinicia o progresso salvo do jogador |
| `/builderwand [jogador]` | Entrega a Builder's Wand |
| `/destroyerhand [jogador]` | Entrega a Destroyer's Hand |
| `/biomewand [jogador]` | Entrega a Biome's Wand |

Os comandos administrativos exigem a permissão de administrador definida pelo plugin.

## Integrações opcionais

- **WorldGuard e GriefPrevention:** respeitam áreas protegidas.
- **Citizens e Sentinel:** permitem reconhecer NPCs durante o combate.
- **PlaceholderAPI:** oferece `%icarusrpg_globallevel%` para plugins de chat, TAB e
  placares.
- **Geyser/Floodgate:** jogadores Bedrock podem entrar normalmente; itens sem um pack
  Bedrock específico usam uma aparência segura do Minecraft.

## Compilação

Para desenvolvimento local:

```bash
mvn package
```

O arquivo final será criado em `target/IcarusRPG-<versão>.jar`. O GitHub Actions
também compila e disponibiliza um novo artefato a cada atualização enviada.

## Contribuições

O IcarusRPG está em desenvolvimento ativo. Relatos de bugs e sugestões podem ser
enviados pela aba [Issues](https://github.com/IcaroFranca/IcarusRPG/issues).
