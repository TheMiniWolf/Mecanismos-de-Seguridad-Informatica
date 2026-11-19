# gen_dns.py - PCAP sintetico para el Caso 2. SOLO escribe a archivo.
import random, string, base64
from scapy.all import Ether, IP, UDP, DNS, DNSQR, DNSRR, wrpcap

random.seed(7)
RESOLVER = "192.168.50.53"
normales = ["www.example.com", "mail.example.com", "intranet.lab.local",
            "updates.example.org", "cdn.example.net", "api.example.com"]
pkts = []
t0 = 1762650000.0

def consulta(t, src, nombre, resp_len=4):
    sport = random.randint(40000, 60000)
    q = Ether()/IP(src=src, dst=RESOLVER)/UDP(sport=sport, dport=53)/DNS(rd=1, qd=DNSQR(qname=nombre))
    q.time = t
    r = Ether()/IP(src=RESOLVER, dst=src)/UDP(sport=53, dport=sport)/DNS(
        qr=1, qd=DNSQR(qname=nombre),
        an=DNSRR(rrname=nombre, type="TXT" if resp_len > 4 else "A",
                 rdata=("x" * resp_len) if resp_len > 4 else "192.0.2.10"))
    r.time = t + 0.01
    pkts.extend([q, r])

# Trafico normal: 5 clientes, 0-300 s
for _ in range(600):
    consulta(t0 + random.uniform(0, 300), f"192.168.50.{random.randint(100,104)}",
             random.choice(normales))

# Cliente 192.168.50.105: consultas con subdominios largos y aleatorios, 120-240 s
t = t0 + 120
while t < t0 + 240:
    datos = base64.b32encode(random.randbytes(30)).decode().lower().rstrip("=")
    consulta(t, "192.168.50.105", f"{datos}.tunel-prueba.example", resp_len=120)
    t += random.uniform(0.3, 0.8)

pkts.sort(key=lambda p: p.time)
wrpcap(r"C:\Taller2\evidencias_originales\dns_sintetico.pcap", pkts)
print("Paquetes:", len(pkts))