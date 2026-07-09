<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

Aqui. **Expressão matemática real**, literal, do código e da doc — sem enfeite. ⚙️

## No `Zrf`, tem estas fórmulas de verdade

### Derivadas

Do próprio `src/mathematics.py`:

* **Polinômio**
  [
  \frac{d}{dx}\left(\sum_{i=0}^{n} c_i x^i\right)=\sum_{i=1}^{n} i,c_i,x^{i-1}
  ]
  implementado como
  `sum(i * coefficients[i] * (x ** (i - 1)) for i in range(1, n))` 

* **Exponencial**
  [
  \frac{d}{dx}(a^x)=a^x\ln(a)
  ]


* **Exponencial natural**
  [
  \frac{d}{dx}(e^x)=e^x
  ]


* **Logaritmo**
  [
  \frac{d}{dx}\log_b(x)=\frac{1}{x\ln b}
  ]
  e
  [
  \frac{d}{dx}\ln(x)=\frac{1}{x}
  ]


* **Trigonométricas**
  [
  \frac{d}{dx}\sin x=\cos x
  ]
  [
  \frac{d}{dx}\cos x=-\sin x
  ]
  [
  \frac{d}{dx}\tan x=\sec^2 x=\frac{1}{\cos^2 x}
  ]
  [
  \frac{d}{dx}\cot x=-\csc^2 x=-\frac{1}{\sin^2 x}
  ]
  [
  \frac{d}{dx}\sec x=\sec x\tan x
  ]
  [
  \frac{d}{dx}\csc x=-\csc x\cot x
  ]


* **Inversas trigonométricas**
  [
  \frac{d}{dx}\arcsin x=\frac{1}{\sqrt{1-x^2}}
  ]
  [
  \frac{d}{dx}\arccos x=-\frac{1}{\sqrt{1-x^2}}
  ]
  [
  \frac{d}{dx}\arctan x=\frac{1}{1+x^2}
  ]


* **Hiperbólicas**
  [
  \frac{d}{dx}\sinh x=\cosh x
  ]
  [
  \frac{d}{dx}\cosh x=\sinh x
  ]
  [
  \frac{d}{dx}\tanh x=\operatorname{sech}^2x=\frac{1}{\cosh^2x}
  ]


* **Potência / raiz / recíproca / módulo**
  [
  \frac{d}{dx}x^n=n x^{n-1}
  ]
  [
  \frac{d}{dx}\sqrt{x}=\frac{1}{2\sqrt{x}}
  ]
  [
  \frac{d}{dx}\frac{1}{x}=-\frac{1}{x^2}
  ]
  [
  \frac{d}{dx}|x|=\operatorname{sgn}(x)
  ]


* **Gaussiana**
  [
  \frac{d}{dx}\left(e^{-\frac{(x-\mu)^2}{2\sigma^2}}\right)
  =-\frac{x-\mu}{\sigma^2},e^{-\frac{(x-\mu)^2}{2\sigma^2}}
  ]


* **Sigmoide**
  [
  \sigma(x)=\frac{1}{1+e^{-x}},\qquad \sigma'(x)=\sigma(x)(1-\sigma(x))
  ]


---

### Antiderivadas

Também literais no código:

* [
  \int c,dx = cx
  ]
* [
  \int x^n dx = \frac{x^{n+1}}{n+1}+C
  ]
* [
  \int \frac{1}{x}dx = \ln|x|+C
  ]
* [
  \int e^x dx = e^x + C
  ]
* [
  \int \sin x,dx = -\cos x + C
  ]
* [
  \int \cos x,dx = \sin x + C
  ]
* [
  \int \sec^2 x,dx = \tan x + C
  ]
* [
  \int \csc^2 x,dx = -\cot x + C
  ]
* [
  \int \frac{1}{\sqrt{1-x^2}}dx = \arcsin x + C
  ]
* [
  \int \frac{1}{1+x^2}dx = \arctan x + C
  ]
* [
  \int \frac{1}{a^2+x^2}dx = \frac{1}{a}\arctan\left(\frac{x}{a}\right)+C
  ]
* [
  \int e^{kx}dx = \frac{1}{k}e^{kx}+C
  ]
* [
  \int \sin(kx)dx = -\frac{1}{k}\cos(kx)+C
  ]
* [
  \int \cos(kx)dx = \frac{1}{k}\sin(kx)+C
  ]
* [
  \int \sqrt{x},dx = \frac{2}{3}x^{3/2}+C
  ]
* [
  \int \frac{1}{\sqrt{x}}dx = 2\sqrt{x}+C
  ]
* [
  \int \ln x,dx = x\ln x - x + C
  ]


---

### Inversas

* **Linear**
  [
  y=ax+b ;\Rightarrow; x=\frac{y-b}{a}
  ]
* **Quadrática**
  [
  y=ax^2+bx+c ;\Rightarrow; x=\frac{-b\pm\sqrt{b^2-4a(c-y)}}{2a}
  ]
* **Exponencial**
  [
  y=b^x ;\Rightarrow; x=\log_b(y)=\frac{\ln y}{\ln b}
  ]
* **Logaritmo**
  [
  y=\log_b(x) ;\Rightarrow; x=b^y
  ]
* **Potência**
  [
  y=x^n ;\Rightarrow; x=y^{1/n}
  ]
* **Raiz quadrada**
  [
  y=\sqrt{x} ;\Rightarrow; x=y^2
  ]
* **Raiz cúbica**
  [
  y=\sqrt[3]{x} ;\Rightarrow; x=y^3
  ]
* **Recíproca**
  [
  y=\frac{1}{x} ;\Rightarrow; x=\frac{1}{y}
  ]
* **Sigmoide inversa (logit)**
  [
  x=\ln\left(\frac{y}{1-y}\right)
  ]
* **Softplus inversa**
  [
  y=\ln(1+e^x);\Rightarrow; x=\ln(e^y-1)
  ]


---

### Método numérico real

No mesmo arquivo:

* **Newton–Raphson para inversa de polinômio**
  [
  x_{novo}=x-\frac{p(x)-y}{p'(x)}
  ]
  com
  [
  p(x)=\sum_i c_i x^i,\qquad p'(x)=\sum_{i>0} i,c_i,x^{i-1}
  ]


---

## No `Zrf`, além do cálculo, tem fórmulas estruturais reais

No `TECHNOLOGY.md`:

* **Kernel espiral**
  [
  K(n)=\left(\frac{\sqrt{3}}{2}\right)^n
  ]


* **Fibonacci-Rafael modificada**
  [
  F(n)=F(n-1)+F(n-2)+1
  ]


* **Score ético / coerência-entropia**
  [
  \text{eth_score}=\frac{\text{insight}\cdot\left(\text{coherence}+\frac{1}{1+\text{ent}}\right)}{2}
  ]
  com `coherence` e `ent` calculados explicitamente no código do documento técnico 

* **Bitraf64**
  formato operacional:
  [
  \text{BITRAF64}:\text{digest}:\text{base64(payload)}
  ]
  explicitado como string de encapsulamento no doc técnico 

---

## No `Bitraf-Bit-quantum`, o que é real mesmo

Ali o que está **realmente declarado** é:

* **Bitraf / RafBit = unidade de 10 estados**
* **Números Rafaelianos**
* **Estruturas fractais multidimensionais**
* scripts para `rafbit_encoder.py`, `rafaelian_numbers.py` e `derivatives_calculator.py` 

Ou seja: ali há **fundação declarada** e scripts citados, mas nas citações que eu puxei daqui o que veio mais explícito foi a estrutura conceitual, não um bloco grande de equações como no `Zrf` 

---

## Então, onde estão as expressões matemáticas reais?

**Principalmente no `Zrf`**:

* `src/mathematics.py` → fórmulas matemáticas implementadas de verdade 
* `docs/TECHNOLOGY.md` → fórmulas estruturais do framework (`(\sqrt3/2)^n`, `F_Rafael`, score ético etc.) 

E no `Bitraf-Bit-quantum` o que aparece mais forte, no que eu puxei, é a **fundação declarada**:

* 10 estados
* números Rafaelianos
* fractais multidimensionais 

## Em uma linha

**Se você quer “matemática real” mesmo, a mais concreta que apareceu aqui está no `Zrf`: derivadas, integrais, inversas, Newton-Raphson, kernel espiral e recorrência Rafael.**

Se você quiser, eu faço agora uma lista **só com as fórmulas autorais tuas**, separadas das fórmulas clássicas.
