# gen_synflood.py - PCAP sintetico para el Caso 1. SOLO escribe a archivo.
import random
from scapy.all import Ether, IP, TCP, wrpcap

random.seed(42)
SERVER = "192.168.50.20"
pkts = []
t0 = 1762650000.0  # marca de tiempo base (epoch)

def add(p, t):
    p.time = t
    pkts.append(p)

def handshake(t, src, sport, complete=True):
    isn = random.randint(1000, 4_000_000)
    add(Ether()/IP(src=src, dst=SERVER)/TCP(sport=sport, dport=80, flags="S", seq=isn), t)
    add(Ether()/IP(src=SERVER, dst=src)/TCP(sport=80, dport=sport, flags="SA", seq=9000, ack=isn+1), t+0.001)
    if complete:
        add(Ether()/IP(src=src, dst=SERVER)/TCP(sport=sport, dport=80, flags="A", seq=isn+1, ack=9001), t+0.002)

clients = [f"192.168.50.{i}" for i in range(100, 110)]

# Fase 1 (0-60 s): trafico normal, ~5 conexiones/s, handshakes completos
t = t0
while t < t0 + 60:
    handshake(t, random.choice(clients), random.randint(40000, 60000))
    t += random.uniform(0.1, 0.3)

# Fase 2 (60-120 s): SYN flood sintetico, ~500 SYN/s, muchas fuentes, sin ACK final
while t < t0 + 120:
    src = f"10.0.{random.randint(0,255)}.{random.randint(1,254)}"
    handshake(t, src, random.randint(1024, 65535), complete=False)
    if random.random() < 0.05:  # algunos clientes legitimos siguen conectando
        handshake(t, random.choice(clients), random.randint(40000, 60000))
    t += 0.002

# Fase 3 (120-180 s): mitigacion (rate limiting), vuelve a lo normal
while t < t0 + 180:
    handshake(t, random.choice(clients), random.randint(40000, 60000))
    t += random.uniform(0.1, 0.3)

pkts.sort(key=lambda p: p.time)
wrpcap(r"C:\Taller2\evidencias_originales\synflood_sintetico.pcap", pkts)
print("Paquetes generados:", len(pkts))