#!/usr/bin/env python3
# ============================================================
# raf_toroide_field_derivation.py — derivacao simbolica exata do
# campo F = curl(A) + alpha*e_phi para o Toroide Rafaelia, a partir
# da formula da imagem:
#
#   F(r,theta,phi) = curl(psi(r,theta,phi) * e_phi) + alpha*e_phi
#   psi(r,theta,phi) = A * sin(n*theta) * cos(m*phi) * exp(-lambda*r)
#
# usando a parametrizacao toroidal real:
#   x = (R0 + r*cos(theta))*cos(phi)
#   y = (R0 + r*cos(theta))*sin(phi)
#   z = r*sin(theta)
#
# Calculo feito com sympy (simbolico, nao aproximado) e verificado
# pela identidade vetorial div(curl(A)) = 0, que tem que valer
# EXATAMENTE para qualquer A -- se nao validar, ha erro na derivacao.
# ============================================================
import sympy as sp


def derive_field():
    r, theta, phi = sp.symbols('r theta phi', real=True, positive=True)
    R0, A_amp, n, m, lam, alpha = sp.symbols('R0 A_amp n m lambda alpha',
                                               real=True, positive=True)

    # fatores de escala da metrica toroidal (derivados da parametrizacao)
    h_r = 1
    h_theta = r
    h_phi = R0 + r * sp.cos(theta)

    psi = A_amp * sp.sin(n * theta) * sp.cos(m * phi) * sp.exp(-lam * r)

    # A = (0, 0, psi) -- apenas componente phi, como na formula original
    A_r, A_theta, A_phi = 0, 0, psi

    # rotacional em coordenadas curvilineas ortogonais (formula padrao
    # de calculo vetorial, ver Arfken ou qualquer texto de eletromag)
    curl_r = sp.simplify((1 / (h_theta * h_phi)) * sp.diff(h_phi * A_phi, theta))
    curl_theta = sp.simplify((1 / (h_phi * h_r)) * (-sp.diff(h_phi * A_phi, r)))
    curl_phi = sp.Integer(0)  # A_r=A_theta=0 -> termo cruzado e zero

    F_r = curl_r
    F_theta = curl_theta
    F_phi = curl_phi + alpha

    return {
        "symbols": dict(r=r, theta=theta, phi=phi, R0=R0, A_amp=A_amp,
                         n=n, m=m, lam=lam, alpha=alpha),
        "metric": dict(h_r=h_r, h_theta=h_theta, h_phi=h_phi),
        "psi": psi,
        "F_r": F_r, "F_theta": F_theta, "F_phi": F_phi,
        "curl_r": curl_r, "curl_theta": curl_theta, "curl_phi": curl_phi,
    }


def verify_divergence_free(deriv):
    """Verifica a identidade div(curl(A)) = 0 -- tem que dar EXATAMENTE
    zero por identidade vetorial; se nao der, ha erro na derivacao."""
    r, theta, phi = deriv["symbols"]["r"], deriv["symbols"]["theta"], deriv["symbols"]["phi"]
    h_r, h_theta, h_phi = deriv["metric"]["h_r"], deriv["metric"]["h_theta"], deriv["metric"]["h_phi"]
    curl_r, curl_theta, curl_phi = deriv["curl_r"], deriv["curl_theta"], deriv["curl_phi"]

    div_curlA = sp.simplify(
        (1 / (h_r * h_theta * h_phi)) * (
            sp.diff(h_theta * h_phi * curl_r, r) +
            sp.diff(h_phi * h_r * curl_theta, theta) +
            sp.diff(h_r * h_theta * curl_phi, phi)
        )
    )
    return div_curlA


if __name__ == "__main__":
    deriv = derive_field()
    print("=== Campo F = curl(psi*e_phi) + alpha*e_phi (derivacao simbolica exata) ===\n")
    print("F_r =")
    sp.pprint(deriv["F_r"])
    print("\nF_theta =")
    sp.pprint(deriv["F_theta"])
    print("\nF_phi =", deriv["F_phi"])

    print("\n=== Verificacao: div(curl(A)) deve ser EXATAMENTE 0 ===")
    result = verify_divergence_free(deriv)
    print("Resultado:", result)
    print("CONFIRMADO" if result == 0 else "ERRO NA DERIVACAO")
