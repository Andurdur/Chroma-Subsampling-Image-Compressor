#!/usr/bin/env python3
import numpy as np
import cv2
from PIL import Image

W, H       = 512, 512            
down_factor = 2                 
mode_subsamp = '420'            
filename   = 'ycbcr_out.bin'    

data = np.fromfile(filename, dtype=np.uint8)
h2, w2 = H//down_factor, W//down_factor
ycbcr = data.reshape((h2, w2, 3))
Y, Cb, Cr = ycbcr[:,:,0], ycbcr[:,:,1], ycbcr[:,:,2]

if mode_subsamp == '420':
    Cb_full = cv2.resize(Cb, (W, H), interpolation=cv2.INTER_LINEAR)
    Cr_full = cv2.resize(Cr, (W, H), interpolation=cv2.INTER_LINEAR)
else:
    Cb_full = cv2.resize(Cb, (W, H), interpolation=cv2.INTER_NEAREST)
    Cr_full = cv2.resize(Cr, (W, H), interpolation=cv2.INTER_NEAREST)

Y_full = cv2.resize(Y, (W, H), interpolation=cv2.INTER_NEAREST)

Yf  = Y_full.astype(np.float32)
Cbf = Cb_full.astype(np.float32) - 128.0
Crf = Cr_full.astype(np.float32) - 128.0

R = 1.164*(Yf - 16.0) + 1.596*Crf
G = 1.164*(Yf - 16.0) - 0.392*Cbf - 0.813*Crf
B = 1.164*(Yf - 16.0) + 2.017*Cbf

rgb = np.clip(np.stack([R,G,B],axis=2), 0, 255).astype(np.uint8)

Image.fromarray(rgb).save('reconstructed.png')
print("Wrote reconstructed.png")
