# Roadmap — Modelador 3D mobile

O Blender tem milhões de linhas de código e mais de 20 anos de desenvolvimento.
Aqui o caminho é ir cobrindo os recursos por fases, na ordem em que mais importam.

## Fase 1 (esta versão)
- Viewport 3D com câmera orbital (1 dedo orbita, 2 dedos zoom e mover)
- Primitivas: cubo, esfera, cilindro, cone, plano, toro
- Selecionar, mover (XZ / Y / tela), girar, escalar, campos numéricos
- Cor, duplicar, excluir, exportar GLB

## Fase 2 — base de projeto + IA
- Salvar e abrir projeto, desfazer/refazer
- Outliner (lista/hierarquia de objetos), multisseleção, snap, pivô
- Luzes e câmeras
- Camada de comandos (cada ação vira um comando) + tela de configuração de IA com provedores

## Fase 3 — modo de edição
- Malha editável (vértices, arestas, faces) com SurfaceTool/ArrayMesh
- Extrudir, inset, bevel, loop cut, unir, separar, subdividir

## Fase 4 — animação
- Keyframes, timeline, curvas de interpolação, loop
- Armature (ossos), pesos de vértices, IK

## Fase 5 — materiais e modificadores
- Materiais PBR, texturas, UV
- Modificadores: espelho, array, subdivisão, solidify

## Fase 6 — avançado
- Escultura, pintura de textura, curvas e texto, render
