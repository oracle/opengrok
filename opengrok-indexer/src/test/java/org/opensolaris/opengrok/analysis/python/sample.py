# MIT License
# 
# Copyright (c) 2017 OsciiArt
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

#!/usr/bin/env python
# -*- coding: utf-8 -*-

from keras.models import model_from_json
import numpy as np
import pandas as pd
from PIL import Image
import pickle
import os


# parameters
model_path = "model/model.json"
weight_path = "model/weight.hdf5"
image_path = 'sample images/original images/21 original.png' # put the path of the image that you convert.
new_width = 0 # adjust the width of the image. the original width is used if new_width = 0.
input_shape = [64, 64, 1]


def add_mergin(img, mergin):
    if mergin!=0:
        img_new = np.ones([img.shape[0] + 2 * mergin, img.shape[1] + 2 * mergin], dtype=np.uint8) * 255
        img_new[mergin:-mergin, mergin:-mergin] = img
    else:
        img_new = img
    return img_new


def pickleload(path):
    with open(path, mode='rb') as f:
        data = pickle.load(f)
    return data


# load model
json_string = open(model_path).read()
model = model_from_json(json_string)
model.load_weights(weight_path)
print("model load done")

char_list_path = "data/char_list.csv"
char_list = pd.read_csv(char_list_path, encoding="cp932")
print("len(char_list)", len(char_list))
# print(char_list.head())
char_list = char_list[char_list['frequency']>=10]
char_list = char_list['char'].as_matrix()

for k, v in enumerate(char_list):
    if v==" ":
        space = k
        break
print("class index of 1B space:", space)


mergin = (input_shape[0] - 18) // 2
img = Image.open(image_path)
orig_width, orig_height = img.size
if new_width==0: new_width = orig_width
new_height = int(img.size[1] * new_width / img.size[0])
img = img.resize((new_width, new_height), Image.LANCZOS)
img = np.array(img)
if len(img.shape) == 3:
    img = img[:, :, 0]

img_new = np.ones([img.shape[0]+2*mergin+18, img.shape[1]+2*mergin+18],
                  dtype=np.uint8) * 255
img_new[mergin:mergin+new_height, mergin:mergin+new_width] = img
img = (img_new.astype(np.float32)) / 255

char_dict_path = "data/char_dict.pkl"
char_dict = pickleload(char_dict_path)

print("len(char_dict)", len(char_dict))

output_dir = "output/"
if not os.path.isdir(output_dir):
    os.makedirs(output_dir)

for slide in range(18):
    print("converting:", slide)
    num_line = (img.shape[0] - input_shape[0]) // 18
    img_width = img.shape[1]
    new_line = np.ones([1, img_width])
    img = np.concatenate([new_line, img], axis=0)
    predicts = []
    text = []
    for h in range(num_line):
        w = 0
        penalty = 1
        predict_line = []
        text_line = ""
        while w <= img_width - input_shape[1]:
            input_img = img[h*18:h*18+ input_shape[0], w:w+input_shape[1]]
            input_img = input_img.reshape([1,input_shape[0], input_shape[1], 1])
            predict = model.predict(input_img)
            if penalty: predict[0, space] = 0
            predict = np.argmax(predict[0])
            penalty = (predict==space)
            char = char_list[predict]
            predict_line.append(char)
            char_width = char_dict[char].shape[1]
            w += char_width
            text_line += char
        predicts.append(predict_line)
        text.append(text_line+'\r\n')
    # print(text)

    img_aa = np.ones_like(img, dtype=np.uint8) * 0xFF

    for h in range(num_line):
        w = 0
        for char in predicts[h]:
            # print("w", w)
            char_width = char_dict[char].shape[1]
            char_img = 255 - char_dict[char].astype(np.uint8) * 255
            img_aa[h*18:h*18+16, w:w+char_width] = char_img
            w += char_width

    img_aa = Image.fromarray(img_aa)
    img_aa = img_aa.crop([0,slide,new_width, new_height+slide])
    save_path = output_dir + os.path.basename(image_path)[:-4] + '_'\
                + 'w' + str(new_width) \
                + '_slide' + str(slide) + '.png'
    img_aa.save(save_path)

    f=open(save_path[:-4] + '.txt', 'w')
    f.writelines(text)
    f.close()
print('http://example.com?a=')
print('''http://example.com?a='b'&''')
