import math, collections
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

def entropia(s):
    c = collections.Counter(s)
    return -sum(v/len(s) * math.log2(v/len(s)) for v in c.values())

filas = []
with open(r"C:\Taller2\evidencias_originales\dns_consultas.tsv", encoding="utf-8-sig") as f:
    for linea in f:
        p = linea.rstrip("\n").split("\t")
        if len(p) < 3 or not p[2]:
            continue
        t, ip, nombre = float(p[0]), p[1], p[2]
        etiqueta = nombre.split(".")[0]
        dominio = ".".join(nombre.split(".")[-2:])
        filas.append((t, ip, nombre, dominio, len(nombre), len(etiqueta), entropia(etiqueta)))

df = pd.DataFrame(filas, columns=["t", "ip", "nombre", "dominio", "long_nombre", "long_etiqueta", "entropia"])
dur = (df.t.max() - df.t.min()) / 60
tabla = df.groupby("ip").agg(consultas=("nombre", "count"),
                             dominios_unicos=("nombre", "nunique"),
                             long_media=("long_nombre", "mean"),
                             entropia_media=("entropia", "mean"))
tabla["consultas_min"] = (tabla.consultas / dur).round(1)
print(tabla.round(2))
tabla.round(2).to_csv(r"C:\Taller2\informe\caso2_tabla_por_host.csv")

dom = df.groupby("dominio").agg(consultas=("nombre", "count"), clientes=("ip", "nunique"),
                                entropia_media=("entropia", "mean")).sort_values("consultas", ascending=False)
print(dom.round(2))
dom.round(2).to_csv(r"C:\Taller2\informe\caso2_tabla_por_dominio.csv")

fig, ax = plt.subplots(figsize=(9, 5))
for ip, g in df.groupby("ip"):
    ax.scatter(g.long_etiqueta, g.entropia, s=12, label=ip, alpha=0.6)
ax.set_xlabel("Longitud de la etiqueta (subdominio)")
ax.set_ylabel("Entropia de Shannon (bits/caracter)")
ax.set_title("Caso 2 - Longitud vs entropia por cliente (PCAP sintetico)")
ax.legend(fontsize=8)
ax.grid(alpha=0.3)
plt.tight_layout()
plt.savefig(r"C:\Taller2\capturas_pantalla\caso2_entropia.png", dpi=150)