import numpy as np

numOfRandAdders = [x+1 for x in range(0,1)]
x = np.linspace(0,5,5000)
y = [2**(b**2) - 1 for b in x]
np.savetxt("../data/datas.csv",np.column_stack((x,y)),fmt="%.5f",delimiter=",",header=f"x,{','.join(map(str,numOfRandAdders))}",comments="")