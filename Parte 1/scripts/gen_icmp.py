# gen_icmp.py - PCAP sintetico para el Caso 7. SOLO escribe a archivo.
import random
from scapy.all import Ether, IP, ICMP, Raw, wrpcap

random.seed(31)
pkts = []
t0 = 1762650000.0

def echo(t, src, dst, tam, ident):
    req = Ether()/IP(src=src, dst=dst)/ICMP(type=8, id=ident, seq=1)/Raw(b"a" * tam)
    rep = Ether()/IP(src=dst, dst=src)/ICMP(type=0, id=ident, seq=1)/Raw(b"a" * tam)
    req.time, rep.time = t, t + 0.002
    pkts.extend([req, rep])

# Diagnostico legitimo: pings de 32 bytes ocasionales, 0-300 s
for i in range(80):
    echo(t0 + random.uniform(0, 300), f"192.168.50.{random.randint(100,103)}", "192.168.50.1", 32, i)

# Posible tunel: 192.168.50.104 -> 192.168.50.20, payload grande y variable, cada ~1 s, 120-240 s
t = t0 + 120
i = 0
while t < t0 + 240:
    pl = bytes(random.getrandbits(8) for _ in range(random.choice([200, 400, 600, 800])))
    req = Ether()/IP(src="192.168.50.104", dst="192.168.50.20")/ICMP(type=8, id=999, seq=i)/Raw(pl)
    rep = Ether()/IP(src="192.168.50.20", dst="192.168.50.104")/ICMP(type=0, id=999, seq=i)/Raw(pl)
    req.time, rep.time = t, t + 0.003
    pkts.extend([req, rep])
    t += random.uniform(0.9, 1.1)
    i += 1

pkts.sort(key=lambda p: p.time)
wrpcap(r"C:\Taller2\evidencias_originales\icmp_sintetico.pcap", pkts)
print("Paquetes:", len(pkts))