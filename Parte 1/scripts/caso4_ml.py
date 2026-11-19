import subprocess, numpy as np, pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from sklearn.ensemble import IsolationForest
from sklearn.metrics import precision_score, recall_score, f1_score, confusion_matrix

TSHARK = r"C:\Program Files\Wireshark\tshark.exe"
BASE = r"C:\Taller2\copias_trabajo"

def extraer(pcap, atacante_inicio, atacante_fin):
    out = subprocess.run([TSHARK, "-r", f"{BASE}\\{pcap}", "-Y", "tcp", "-T", "fields",
        "-e", "frame.time_epoch", "-e", "ip.src", "-e", "tcp.dstport", "-e", "tcp.flags.syn",
        "-e", "tcp.flags.ack", "-e", "tcp.flags.reset", "-e", "frame.len"],
        capture_output=True, text=True, check=True).stdout
    filas = []
    for l in out.splitlines():
        f = l.split("\t")
        if len(f) < 7 or "" in f[:6]:
            continue
        filas.append((float(f[0]), f[1], int(f[2]), f[3] == "True", f[4] == "True", f[5] == "True", int(f[6])))
    d = pd.DataFrame(filas, columns=["t", "src", "dport", "syn", "ack", "rst", "len"])
    d["v"] = ((d.t - d.t.min()) // 10).astype(int)
    r = d.groupby("v").apply(lambda g: pd.Series({
        "pkts": len(g),
        "syn_sin_ack": ((g.syn) & (~g.ack)).sum(),
        "rst": g.rst.sum(),
        "srcs": g.src.nunique(),
        "puertos": g.dport.nunique(),
        "bytes": g.len.sum()}), include_groups=False)
    r["etiqueta"] = [(1 if atacante_inicio <= v * 10 < atacante_fin else 0) for v in r.index]
    r["dataset"] = pcap
    return r

df = pd.concat([extraer("synflood_sintetico.pcap", 60, 120),
                extraer("scan_sintetico.pcap", 120, 140)], ignore_index=True)
cols = ["pkts", "syn_sin_ack", "rst", "srcs", "puertos", "bytes"]
df.to_csv(r"C:\Taller2\informe\caso4_dataset.csv", index=False)

# Separacion temporal por dataset: entrenamiento = primeras ventanas SIN ataque (< 60% del tiempo), prueba = resto
tr_idx, te_idx = [], []
for nombre, g in df.groupby("dataset"):
    n = len(g)
    corte = int(n * 0.4)
    tr = g.iloc[:corte]
    tr_idx += list(tr[tr.etiqueta == 0].index)
    te_idx += list(g.iloc[corte:].index)
X_tr, X_te, y_te = df.loc[tr_idx, cols], df.loc[te_idx, cols], df.loc[te_idx, "etiqueta"]

# Linea base estadistica: z-score maximo > 3 sobre las caracteristicas
mu, sd = X_tr.mean(), X_tr.std().replace(0, 1)
z = ((X_te - mu) / sd).abs().max(axis=1)
pred_est = (z > 3).astype(int)

# Isolation Forest
iso = IsolationForest(n_estimators=200, contamination="auto", random_state=42).fit(X_tr)
score = -iso.score_samples(X_te)
pred_iso = (iso.predict(X_te) == -1).astype(int)

for nombre, p in [("Linea base (z>3)", pred_est), ("IsolationForest", pred_iso)]:
    print(f"\n{nombre}: precision={precision_score(y_te, p, zero_division=0):.2f} "
          f"recall={recall_score(y_te, p, zero_division=0):.2f} F1={f1_score(y_te, p, zero_division=0):.2f}")
    print("Matriz de confusion [[TN FP],[FN TP]]:\n", confusion_matrix(y_te, p, labels=[0, 1]))
print("\nVentanas entrenamiento:", len(X_tr), "| prueba:", len(X_te), "| ataque en prueba:", int(y_te.sum()))

from sklearn.metrics import roc_auc_score, average_precision_score
# Umbral calibrado: percentil 99 de los scores de entrenamiento
score_tr = -iso.score_samples(X_tr)
umbral = np.percentile(score_tr, 99)
pred_cal = (score > umbral).astype(int)
print(f"\nIsolationForest calibrado (p99): precision={precision_score(y_te, pred_cal, zero_division=0):.2f} "
      f"recall={recall_score(y_te, pred_cal, zero_division=0):.2f} F1={f1_score(y_te, pred_cal, zero_division=0):.2f}")
print(confusion_matrix(y_te, pred_cal, labels=[0, 1]))
print(f"ROC-AUC IsolationForest: {roc_auc_score(y_te, score):.3f} | PR-AUC: {average_precision_score(y_te, score):.3f}")
print(f"ROC-AUC z-score max:     {roc_auc_score(y_te, z):.3f} | PR-AUC: {average_precision_score(y_te, z):.3f}")

fig, ax = plt.subplots(figsize=(10, 4))
ax.scatter(range(len(score)), score, c=y_te.values, cmap="coolwarm", s=25)
ax.set_xlabel("Ventana de prueba (10 s)"); ax.set_ylabel("Score de anomalia")
ax.set_title("Caso 4 - Score IsolationForest (rojo = ventana de ataque)")
ax.grid(alpha=0.3); plt.tight_layout()
plt.savefig(r"C:\Taller2\capturas_pantalla\caso4_scores.png", dpi=150)