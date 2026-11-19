import subprocess
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

TSHARK = r"C:\Program Files\Wireshark\tshark.exe"
PCAP = r"C:\Taller2\copias_trabajo\synflood_sintetico.pcap"

out = subprocess.run(
    [TSHARK, "-r", PCAP, "-T", "fields",
     "-e", "frame.time_epoch", "-e", "tcp.flags.syn", "-e", "tcp.flags.ack", "-e", "tcp.len"],
    capture_output=True, text=True, check=True).stdout

rows = []
for line in out.splitlines():
    f = line.split("\t")
    if len(f) < 4 or "" in f:
        continue
    rows.append((float(f[0]), (1 if f[1]=="True" else 0), (1 if f[2]=="True" else 0), int(f[3])))

df = pd.DataFrame(rows, columns=["t", "syn", "ack", "len"])
df["seg"] = (df["t"] - df["t"].min()).astype(int)

syn = df[(df.syn == 1) & (df.ack == 0)].groupby("seg").size()
synack = df[(df.syn == 1) & (df.ack == 1)].groupby("seg").size()
ack = df[(df.syn == 0) & (df.ack == 1) & (df.len == 0)].groupby("seg").size()

idx = range(0, int(df.seg.max()) + 1)
res = pd.DataFrame({"SYN": syn, "SYN/ACK": synack, "ACK": ack}, index=idx).fillna(0)
res.to_csv(r"C:\Taller2\informe\caso1_serie_temporal.csv")

fig, ax = plt.subplots(figsize=(11, 5))
res.plot(ax=ax)
ax.axvspan(60, 120, color="red", alpha=0.1, label="Fase de pico")
ax.set_xlabel("Tiempo (s)")
ax.set_ylabel("Paquetes por segundo")
ax.set_title("Caso 1 - SYN, SYN/ACK y ACK por segundo (PCAP sintetico)")
ax.legend()
ax.grid(alpha=0.3)
plt.tight_layout()
plt.savefig(r"C:\Taller2\capturas_pantalla\caso1_syn_synack_ack.png", dpi=150)

for nombre, a, b in [("Normal (0-60 s)", 0, 60), ("Pico (60-120 s)", 60, 120), ("Mitigacion (120-180 s)", 120, 180)]:
    s = res.loc[a:b - 1]
    print(f"{nombre}: SYN/s={s['SYN'].mean():.1f}  SYN/ACK/s={s['SYN/ACK'].mean():.1f}  ACK/s={s['ACK'].mean():.1f}")
