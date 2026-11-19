import subprocess, json
import pandas as pd

TSHARK = r"C:\Program Files\Wireshark\tshark.exe"
BASE = r"C:\Taller2\copias_trabajo"

def tshark(pcap, filtro, campos):
    cmd = [TSHARK, "-r", f"{BASE}\\{pcap}", "-Y", filtro, "-T", "fields"]
    for c in campos:
        cmd += ["-e", c]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True).stdout
    return [l.split("\t") for l in out.splitlines()]

eventos = []  # esquema comun: t, fuente_log, host, tipo, detalle

def agrega(pcap, filtro, tipo, fuente, host_idx=1, detalle=""):
    for f in tshark(pcap, filtro, ["frame.time_epoch", "ip.src", "ip.dst"]):
        if len(f) >= 3 and f[1]:
            eventos.append({"t": float(f[0]), "fuente": fuente, "host": f[host_idx], "tipo": tipo, "detalle": detalle})

# Reglas de deteccion simples como fuentes distintas (simulan firewall/IDS/DNS/ICMP)
agrega("scan_sintetico.pcap", "tcp.flags.syn==1 && tcp.flags.ack==0 && ip.src==192.168.50.105", "escaneo_puertos", "ids")
agrega("rst_sintetico.pcap", "tcp.flags.reset==1 && ip.src==192.168.50.20", "rst_servidor", "firewall", host_idx=2)
agrega("dns_sintetico.pcap", "dns.flags.response==0 && dns.qry.name.len>50", "dns_nombre_largo", "dns")
agrega("icmp_sintetico.pcap", "icmp.type==8 && frame.len>100", "icmp_payload_grande", "ids")
agrega("reflexion_sintetico.pcap", "udp.srcport==53 && ip.dst==192.168.50.110", "respuesta_sin_peticion", "dns", host_idx=2)
agrega("synflood_sintetico.pcap", "tcp.flags.syn==1 && tcp.flags.ack==0 && ip.src==10.0.0.0/16", "syn_sin_handshake", "firewall", host_idx=2)

df = pd.DataFrame(eventos).drop_duplicates()
df["ventana"] = (df.t // 60).astype(int)
pesos = {"escaneo_puertos": 40, "rst_servidor": 15, "dns_nombre_largo": 30, "icmp_payload_grande": 25,
         "respuesta_sin_peticion": 35, "syn_sin_handshake": 35}

# Correlacion por host: puntuacion = suma de pesos de tipos distintos + bonus por multiples fuentes
res = []
for h, g in df.groupby("host"):
    tipos = g.tipo.unique()
    score = sum(pesos[t] for t in tipos) + (10 if g.fuente.nunique() > 1 else 0)
    res.append({"host": h, "eventos": len(g), "tipos": ", ".join(tipos), "fuentes": g.fuente.nunique(),
                "puntuacion": score, "prioridad": "ALTA" if score >= 40 else "MEDIA" if score >= 20 else "BAJA"})
r = pd.DataFrame(res).sort_values("puntuacion", ascending=False)
print(r.to_string(index=False))
r.to_csv(r"C:\Taller2\informe\caso9_prioridades.csv", index=False)

# Alerta enriquecida (se puede dar a un LLM SOLO anonimizada) + registro de validacion humana
top = r.iloc[0]
alerta = {"host": top.host, "puntuacion": int(top.puntuacion), "prioridad": top.prioridad,
          "indicadores": top.tipos.split(", "), "eventos": int(top.eventos),
          "explicacion": f"Puntuacion = suma de pesos por tipo de indicador distinto ({top.tipos}) + 10 si hay >1 fuente",
          "validacion_humana": {"revisor": "[tu nombre]", "decision": "PENDIENTE", "accion": "ninguna automatica", "reversible": True}}
open(r"C:\Taller2\informe\caso9_alerta_enriquecida.json", "w", encoding="utf-8").write(json.dumps(alerta, indent=2, ensure_ascii=False))
print(json.dumps(alerta, indent=2, ensure_ascii=False))
