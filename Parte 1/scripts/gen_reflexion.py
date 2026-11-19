# gen_reflexion.py - PCAP sintetico para el Caso 6. SOLO escribe a archivo.
import random
from scapy.all import Ether, IP, UDP, DNS, DNSQR, DNSRR, Raw, wrpcap

random.seed(21)
SERVIDOR = "192.168.50.53"
pkts = []
t0 = 1762650000.0

def add(p, t):
    p.time = t
    pkts.append(p)

def dns(t, cliente, nombre, tam_resp):
    sport = random.randint(40000, 60000)
    add(Ether()/IP(src=cliente, dst=SERVIDOR)/UDP(sport=sport, dport=53)/DNS(rd=1, qd=DNSQR(qname=nombre)), t)
    add(Ether()/IP(src=SERVIDOR, dst=cliente)/UDP(sport=53, dport=sport)/DNS(
        qr=1, qd=DNSQR(qname=nombre), an=DNSRR(rrname=nombre, type="TXT", rdata="x" * tam_resp)), t + 0.01)

# Fase normal 0-120 s: 5 clientes, respuestas pequenas
for _ in range(400):
    dns(t0 + random.uniform(0, 120), f"192.168.50.{random.randint(100,104)}", "www.example.com", 20)

# Fase reflexion 120-180 s: respuestas grandes hacia una victima, SIN solicitud previa
VICTIMA = "192.168.50.110"
t = t0 + 120
while t < t0 + 180:
    add(Ether()/IP(src=SERVIDOR, dst=VICTIMA)/UDP(sport=53, dport=random.randint(1024, 65535))/DNS(
        qr=1, qd=DNSQR(qname="isc.example", qtype=255),
        an=DNSRR(rrname="isc.example", type="TXT", rdata="x" * 900)), t)
    t += 0.02

pkts.sort(key=lambda p: p.time)
wrpcap(r"C:\Taller2\evidencias_originales\reflexion_sintetico.pcap", pkts)
print("Paquetes:", len(pkts))
