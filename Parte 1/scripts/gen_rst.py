# gen_rst.py - PCAP sintetico para el Caso 5. SOLO escribe a archivo.
import random
from scapy.all import Ether, IP, TCP, Raw, wrpcap

random.seed(11)
SERVER = "192.168.50.20"
pkts = []
t0 = 1762650000.0

def add(p, t):
    p.time = t
    pkts.append(p)

def sesion(t, src, sport, cierre):
    isn, isn_s = random.randint(1000, 10**6), random.randint(1000, 10**6)
    c = Ether()/IP(src=src, dst=SERVER)
    s = Ether()/IP(src=SERVER, dst=src)
    add(c/TCP(sport=sport, dport=80, flags="S", seq=isn), t)
    add(s/TCP(sport=80, dport=sport, flags="SA", seq=isn_s, ack=isn+1), t+0.001)
    add(c/TCP(sport=sport, dport=80, flags="A", seq=isn+1, ack=isn_s+1), t+0.002)
    add(c/TCP(sport=sport, dport=80, flags="PA", seq=isn+1, ack=isn_s+1)/Raw(b"GET / HTTP/1.1\r\n\r\n"), t+0.01)
    add(s/TCP(sport=80, dport=sport, flags="PA", seq=isn_s+1, ack=isn+19)/Raw(b"HTTP/1.1 200 OK\r\n\r\n"), t+0.02)
    if cierre == "fin":      # cierre legitimo
        add(c/TCP(sport=sport, dport=80, flags="FA", seq=isn+19, ack=isn_s+20), t+0.03)
        add(s/TCP(sport=80, dport=sport, flags="FA", seq=isn_s+20, ack=isn+20), t+0.031)
        add(c/TCP(sport=sport, dport=80, flags="A", seq=isn+20, ack=isn_s+21), t+0.032)
    elif cierre == "rst_app":  # reset del cliente (app cerrada abruptamente)
        add(c/TCP(sport=sport, dport=80, flags="RA", seq=isn+19, ack=isn_s+20), t+0.03)
    elif cierre == "rst_fuera":  # RST fuera de contexto: seq incorrecto, cliente sigue enviando
        add(s/TCP(sport=80, dport=sport, flags="R", seq=isn_s+99999), t+0.03)
        add(c/TCP(sport=sport, dport=80, flags="PA", seq=isn+19, ack=isn_s+20)/Raw(b"x"), t+0.05)

clientes = [f"192.168.50.{i}" for i in range(100, 106)]
for i in range(300):
    t = t0 + i * random.uniform(0.5, 1.0)
    sesion(t, random.choice(clientes), random.randint(40000, 60000), "fin")
for i in range(30):   # resets benignos del cliente
    sesion(t0 + random.uniform(0, 200), random.choice(clientes), random.randint(40000, 60000), "rst_app")
for i in range(25):   # RST sospechosos hacia 192.168.50.103, 120-150 s
    sesion(t0 + 120 + i * 1.2, "192.168.50.103", random.randint(40000, 60000), "rst_fuera")

pkts.sort(key=lambda p: p.time)
wrpcap(r"C:\Taller2\evidencias_originales\rst_sintetico.pcap", pkts)
print("Paquetes:", len(pkts))