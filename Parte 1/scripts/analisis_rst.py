import subprocess
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

TSHARK = r"C:\Program Files\Wireshark\tshark.exe"
PCAP = r"C:\Taller2\copias_trabajo\rst_sintetico.pcap"

out = subprocess.run([TSHARK, "-r", PCAP, "-T", "fields", "-e", "frame.time_epoch", "-e", "tcp.stream",
                      "-e", "ip.src", "-e", "ip.dst", "-e", "tcp.flags.reset", "-e", "tcp.flags.fin",
                      "-e", "tcp.len"], capture_output=True, text=True, check=True).stdout
rows = []
for l in out.splitlines():
    f = l.split("\t")
    if len(f) < 7 or "" in f[:6]:
        continue
    rows.append((float(f[0]), int(f[1]), f[2], f[3], f[4] == "True", f[5] == "True", int(f[6] or 0)))
df = pd.DataFrame(rows, columns=["t", "stream", "src", "dst", "rst", "fin", "len"])
t0 = df.t.min()

res = []
for s, g in df.groupby("stream"):
    g = g.sort_values("t")
    if not g.rst.any():
        continue
    i = g.index[g.rst][0]
    rst = g.loc[i]
    despues = g[(g.t > rst.t) & (g.src != rst.src) & (g.len > 0)]
    res.append({"stream": s, "t": rst.t - t0, "emisor": rst.src, "receptor": rst.dst,
                "datos_posteriores": len(despues),
                "clase": "sospechoso" if len(despues) > 0 else "benigno"})
r = pd.DataFrame(res)
print(r.groupby(["emisor", "receptor", "clase"]).size().reset_index(name="flujos"))
print("Ventana sospechosos (s):", r[r.clase == "sospechoso"].t.min().round(1), "-", r[r.clase == "sospechoso"].t.max().round(1))
r.round(2).to_csv(r"C:\Taller2\informe\caso5_flujos_rst.csv", index=False)

fig, ax = plt.subplots(figsize=(10, 4))
for c, col in [("benigno", "tab:blue"), ("sospechoso", "tab:red")]:
    x = r[r.clase == c]
    ax.scatter(x.t, [c] * len(x), s=18, color=col, alpha=0.7)
ax.set_xlabel("Tiempo (s)")
ax.set_title("Caso 5 - RST por clase en el tiempo (PCAP sintetico)")
ax.grid(alpha=0.3)
plt.tight_layout()
plt.savefig(r"C:\Taller2\capturas_pantalla\caso5_rst_tiempo.png", dpi=150)