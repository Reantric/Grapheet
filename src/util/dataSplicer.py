import numpy as np

numOfRandAdders = [x+1 for x in range(0,1)]
x = np.linspace(1000000,670000000,15000)
y = [abs(np.sqrt(b/10) * np.cos(b/30000000))**abs(np.sin(b/10000000)) for b in x]
np.savetxt("../data/datas.csv",np.column_stack((x,y)),fmt="%.5f",delimiter=",",header=f"x,{','.join(map(str,numOfRandAdders))}",comments="")
