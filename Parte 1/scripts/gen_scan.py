# gen_scan.py - PCAP sintetico para el Caso 8. SOLO escribe a archivo.
import random
from scapy.all import Ether, IP, TCP, wrpcap

random.seed(41)
SERVER = "192.168.50.20"
ABIERTOS = {22, 80, 443, 3306}
pkts = []
t0 = 1762650000.0

def add(p, t):
    p.time = t
    pkts.append(p)

def conexion(t, src, dport, completa=True):
    sport = random.randint(40000, 60000)
    isn = random.randint(1000, 10**6)
    add(Ether()/IP(src=src, dst=SERVER)/TCP(sport=sport, dport=dport, flags="S", seq=isn), t)
    if dport in ABIERTOS:
        add(Ether()/IP(src=SERVER, dst=src)/TCP(sport=dport, dport=sport, flags="SA", seq=5000, ack=isn+1), t+0.001)
        if completa:
            add(Ether()/IP(src=src, dst=SERVER)/TCP(sport=sport, dport=dport, flags="A", seq=isn+1, ack=5001), t+0.002)
        else:
            add(Ether()/IP(src=src, dst=SERVER)/TCP(sport=sport, dport=dport, flags="R", seq=isn+1), t+0.002)
    else:
        add(Ether()/IP(src=SERVER, dst=src)/TCP(sport=dport, dport=sport, flags="RA", seq=0, ack=isn+1), t+0.001)

# Trafico normal: clientes usando 80/443, 0-300 s
for _ in range(300):
    conexion(t0 + random.uniform(0, 300), f"192.168.50.{random.randint(100,103)}", random.choice([80, 443]))

# Escaneo: 192.168.50.105 prueba 1-1024 y algunos mas, secuencial, 120-135 s
t = t0 + 120
for puerto in list(range(1, 1025)) + [3306, 3389, 5432, 8080, 8443]:
    conexion(t, "192.168.50.105", puerto, completa=False)
    t += 0.015

pkts.sort(key=lambda p: p.time)
wrpcap(r"C:\Taller2\evidencias_originales\scan_sintetico.pcap", pkts)
print("Paquetes:", len(pkts))