# RAFAELIA — Dossiê Matemático Computável

Este relatório separa, com rigor reproduzível, o que está em nível de:
- fórmula matemática explícita;
- heurística computacional definida;
- contêiner de integridade/serialização;
- teste de falsificabilidade executável.

## Fórmulas e contexto

### pythagoras
- **Metáfora pedagógica**: Composição geométrica de dois eixos em um terceiro eixo resultante.
- **Termo técnico**: Teorema de Pitágoras
- **Formulação acadêmica**: Em triângulo retângulo, a soma dos quadrados dos catetos é igual ao quadrado da hipotenusa.
- **LaTeX**: `$a^2 + b^2 = c^2$
- **Modo de verificação**: `symbolic`
- **Variáveis**:
  - `a`: cateto 1
  - `b`: cateto 2
  - `c`: hipotenusa

### bhaskara_delta
- **Metáfora pedagógica**: Marcador de regime da equação quadrática.
- **Termo técnico**: Discriminante de Bhaskara
- **Formulação acadêmica**: O discriminante determina a natureza das raízes de uma equação quadrática.
- **LaTeX**: `$\Delta = b^2 - 4ac$
- **Modo de verificação**: `symbolic`
- **Variáveis**:
  - `a`: coeficiente quadrático
  - `b`: coeficiente linear
  - `c`: termo constante
  - `Δ`: discriminante

### fibonacci
- **Metáfora pedagógica**: Crescimento recursivo com memória curta de dois estados.
- **Termo técnico**: Recorrência de Fibonacci
- **Formulação acadêmica**: Cada termo é a soma dos dois termos anteriores, a partir de condições iniciais.
- **LaTeX**: `$F_n = F_{n-1} + F_{n-2}$
- **Modo de verificação**: `algorithmic`
- **Variáveis**:
  - `F_n`: n-ésimo termo
  - `F_0`: termo inicial 0
  - `F_1`: termo inicial 1

### inverse_ethics
- **Metáfora pedagógica**: Amortecimento escalar decrescente sobre crescimento de Fibonacci.
- **Termo técnico**: Função racional de amortecimento baseada em Fibonacci
- **Formulação acadêmica**: A função E_n = 1 / (1 + F_n / φ) decresce para zero conforme F_n cresce.
- **LaTeX**: `$E_n = \frac{1}{1 + \frac{F_n}{\varphi}}$
- **Modo de verificação**: `mixed`
- **Variáveis**:
  - `E_n`: índice amortecido
  - `F_n`: n-ésimo Fibonacci
  - `φ`: razão áurea

### equilateral_height
- **Metáfora pedagógica**: Altura normalizada do triângulo equilátero de lado 1.
- **Termo técnico**: Altura do triângulo equilátero
- **Formulação acadêmica**: Para lado unitário, a altura do triângulo equilátero é √3/2.
- **LaTeX**: `$h = \frac{\sqrt{3}}{2}$
- **Modo de verificação**: `symbolic`
- **Variáveis**:
  - `h`: altura
  - `lado`: comprimento do lado

### torus_x
- **Metáfora pedagógica**: Componente observável de uma dinâmica toroidal.
- **Termo técnico**: Parametrização parcial de toro
- **Formulação acadêmica**: A coordenada x de um toro parametrizado por (θ, φ) é (R + r cos φ) cos θ.
- **LaTeX**: `$x = (R + r \cos \phi)\cos \theta$
- **Modo de verificação**: `numeric`
- **Variáveis**:
  - `R`: raio maior
  - `r`: raio menor
  - `θ`: ângulo longitudinal
  - `φ`: ângulo meridional

### shannon_entropy
- **Metáfora pedagógica**: Medida da dispersão informacional do sistema.
- **Termo técnico**: Entropia de Shannon
- **Formulação acadêmica**: A entropia mede a incerteza média de uma distribuição discreta.
- **LaTeX**: `$H = -\sum_i p_i \log_2 p_i$
- **Modo de verificação**: `symbolic`
- **Variáveis**:
  - `p_i`: probabilidade do i-ésimo símbolo

### base_period
- **Metáfora pedagógica**: Comprimento do ciclo repetitivo de 1/n em uma base.
- **Termo técnico**: Período da expansão periódica de 1/n em base b
- **Formulação acadêmica**: Para n coprimo com b, o período de 1/n em base b coincide com a ordem multiplicativa de b módulo n.
- **LaTeX**: `$P_b(n) = \operatorname{ord}_n(b)$
- **Modo de verificação**: `mixed`
- **Variáveis**:
  - `P_b(n)`: comprimento do período
  - `b`: base
  - `n`: denominador

### hamming
- **Metáfora pedagógica**: Distância mínima entre duas assinaturas binárias.
- **Termo técnico**: Distância de Hamming
- **Formulação acadêmica**: A distância de Hamming entre dois vetores binários é o número de posições em que diferem.
- **LaTeX**: `$d_H(x,y) = \sum_i [x_i \neq y_i]$
- **Modo de verificação**: `algorithmic`
- **Variáveis**:
  - `x,y`: vetores binários

### zipraf_container
- **Metáfora pedagógica**: Encapsulamento de payload com cabeçalho, compressão e integridade.
- **Termo técnico**: Contêiner serializado com compressão e hash de integridade
- **Formulação acadêmica**: O payload é formado por cabeçalho e corpo, comprimido por zlib e validado por SHA3-256.
- **LaTeX**: `$h = \operatorname{SHA3\mbox{-}256}(\operatorname{zlib}(payload))$
- **Modo de verificação**: `algorithmic`
- **Variáveis**:
  - `payload`: header || body
  - `h`: hash de integridade

## Resultados dos testes

**Passaram**: 10/10

### pythagoras :: identidade_simbólica
- **Status**: `PASS`
- **Descrição**: Verificação simbólica direta de a²+b²-c² = 0.
- **Métricas**: `{"simplified_expression": "0"}`

### bhaskara_delta :: definição_simbólica
- **Status**: `PASS`
- **Descrição**: Registro simbólico do discriminante; falsificação depende de substituições numéricas.
- **Métricas**: `{"delta_latex": "- 4 a c + b^{2}", "polynomial": "a*x**2 + b*x + c"}`

### fibonacci :: recorrência_iterativa
- **Status**: `PASS`
- **Descrição**: Checagem exaustiva da recorrência para n=2..34.
- **Métricas**: `{"checked_until": 34, "bad_index": null}`

### inverse_ethics :: monotonia_e_amortecimento
- **Status**: `PASS`
- **Descrição**: A função deve decrescer conforme Fibonacci cresce.
- **Métricas**: `{"E0": 1.0, "E5": 0.24448861874998185, "E19": 0.0003868471793506944}`

### equilateral_height :: dedução_por_pitágoras
- **Status**: `PASS`
- **Descrição**: Altura derivada do triângulo retângulo formado pela mediana.
- **Métricas**: `{"simplified": "sqrt(3)/2", "target": "sqrt(3)/2"}`

### torus_x :: faixa_numérica
- **Status**: `PASS`
- **Descrição**: A componente x deve ficar no intervalo [-(R+r), +(R+r)].
- **Métricas**: `{"xmin": -0.9999999999755501, "xmax": 3.0, "bound": 3.0}`

### shannon_entropy :: casos_extremos
- **Status**: `PASS`
- **Descrição**: Arquivo constante deve ter entropia ~0; distribuição uniforme de 256 bytes deve se aproximar de 8.
- **Métricas**: `{"constant_entropy": -0.0, "uniform_entropy": 8.0}`

### base_period :: ordem_multiplicativa_e_terminação
- **Status**: `PASS`
- **Descrição**: Casos clássicos conhecidos de período periódico e terminante.
- **Métricas**: `{"base10_n3": 1, "base10_n7": 6, "base2_n3": 2, "base10_n8": 0}`

### hamming :: distância_total
- **Status**: `PASS`
- **Descrição**: Entre 256 bits 0 e 256 bits 1, a distância deve ser 256.
- **Métricas**: `{"distance": 256}`

### zipraf_container :: roundtrip_com_integridade
- **Status**: `PASS`
- **Descrição**: Encode/decode deve preservar cabeçalho e corpo quando integridade passa.
- **Métricas**: `{"encoded_prefix": "zipraf:zipraf-1.1:sha3:60671af1f0e0536f3", "len_encoded": 224}`

## Figuras geradas

- `inverse_ethics_curve.png`
- `torus_x_curve.png`
- `entropy_reference.png`

## Leitura de rigor

Este pipeline não pretende provar qualquer afirmação metafísica ampla. Ele formaliza, testa e documenta apenas:
1. identidades matemáticas explícitas;
2. propriedades computacionais verificáveis;
3. contêineres de integridade e reconstrução;
4. critérios de falsificabilidade executáveis.
