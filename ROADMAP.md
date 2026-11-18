# Roadmap — Modelador 3D Android nativo

O Blender tem milhões de linhas de código e mais de 20 anos de desenvolvimento.
Aqui o caminho é ir cobrindo os recursos por fases, na ordem em que mais importam.

## Fase 1 — fundação nativa
- Viewport OpenGL ES com cubo e câmera orbital
- Touch, mouse, roda do mouse e teclado
- Arquitetura Android nativa sem Godot
- Tela inicial de configuração de provedores de IA

## Fase 2 — cena e IA
- Salvar e abrir projeto, desfazer/refazer
- Outliner (lista/hierarquia de objetos), multisseleção, snap, pivô
- Luzes e câmeras
- Camada de comandos (cada ação vira um comando)
- Adaptadores Groq, OpenAI, Anthropic, Gemini e OpenRouter

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
