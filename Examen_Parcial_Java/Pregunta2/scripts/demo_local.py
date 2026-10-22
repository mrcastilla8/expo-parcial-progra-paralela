"""Inicia cuatro JVM independientes. Ejecutar con Python 3 desde cualquier carpeta."""
from pathlib import Path
import os, subprocess, time, sys
base=Path(__file__).resolve().parent.parent
os.chdir(base)
java=str(Path(os.environ['JAVA_HOME'])/'bin'/'java') if 'JAVA_HOME' in os.environ else 'java'
processes=[]
def run(*args):
    subprocess.run([java,'-cp','build',*args],check=True)
try:
    run('Generador','dataset/transacciones.csv',sys.argv[1] if len(sys.argv)>1 else '100000')
    run('Coordinador','serial','dataset/transacciones.csv','resultados')
    processes.append(subprocess.Popen([java,'-cp','build','Coordinador','maestro','config/cluster.properties','dataset/transacciones.csv','resultados']))
    time.sleep(1)
    for i in range(1,4):
        processes.append(subprocess.Popen([java,'-cp','build','Trabajador','config/cluster.properties',f'nodo{i}',f'config/nodo{i}-ip.txt',f'trabajo/nodo{i}']))
    for p in processes:
        if p.wait(timeout=180)!=0: raise RuntimeError('Un proceso fallo; revisar mensajes')
    run('Coordinador','comparar','resultados')
finally:
    for p in processes:
        if p.poll() is None: p.kill()
