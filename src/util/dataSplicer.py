import numpy as np

precision = 4
it = 10**7
numOfRandAdders = [x+1 for x in range(0,3)]
summedRandomNumbers = np.row_stack([np.sum(np.random.rand(it,rand),axis=1) for rand in numOfRandAdders])
#multiplierCoefficients = np.array([1/2**x for x in range(n+1)])
#RandomNumbers *= multiplierCoefficients

f = lambda k,v: np.fromiter((np.mean(k > a) for a in v),dtype=float,count=len(v))
x = np.linspace(0,1,10*1+1)
y = np.column_stack([f(b,x) for b in summedRandomNumbers])

np.savetxt("../data/datas.csv",np.column_stack((x,y)),fmt="%.5f",delimiter=",",header=f"x,{','.join(map(str,numOfRandAdders))}",comments="")