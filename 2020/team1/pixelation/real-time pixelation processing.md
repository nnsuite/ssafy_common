[Blur and anonymize faces with OpenCV and Python - PyImageSearch](https://www.pyimagesearch.com/2020/04/06/blur-and-anonymize-faces-with-opencv-and-python/)

4단계

1. face detection
2. x, y좌표로 ROI(Region of Interest) 추출
3. ROI에 가우시안 블러 적용
4. 오리지널 이미지에 블러 적용된 이미지 저장

# OpenCV

> Open Source Computer Vision Library 영상(사진), 동영상 처리에 사용할 수 있는 오픈소스 라이브러리

## 1. 설치

[pip install opencv - PyImageSearch](https://www.pyimagesearch.com/2018/09/19/pip-install-opencv/)

`pip`로 설치할 수 있는 4가지의 `OpenCV`가 있다.

1. `opencv-python` : 메인 모듈이 포함된 라이브러리
2. `opencv-contrib-python` : 메인모듈과 contrib 모듈이 포함된 라이브러리(추천!)
3. `opencv-python-headless` : 1번과 같지만 GUI 기능 없는 라이브러리
4. `opencv-contrib-python-headless` : 2번과 같지만 GUI 기능 없는 라이브러리

```bash
$ sudo pip install opencv-contrib-python
```

- 가상환경에 설치하는 것을 추천

## 2. 이미지 다뤄보기

- `cv2.imread({경로}, {옵션})` : 이미지 읽기

- `cv2.imshow(title, image)` : title은 창 제목, image는 `cv.imread()`의 return 값이다.

- ```
  image.shape
  ```

   : 이미지 파일의 모양 - height, width, channel

  - `channel`은 이미지의 색상 정보를 의미하며, 유표 비트가 클 수록 더 정밀해진다.

```python
# 예시
import numpy as np
import cv2

image = cv2.imread("../examples/captain-america.jpg") # 경로 입력
cv2.imshow("captain-america", image);cv2.waitKey(0)
```

## 3. 이미지 필터링

OpenCV에서 여러 방법으로 이미지에 `kernel(filter)`를 적용할 수 있다. 여기서 `kernel`은 행렬을 의미하는데, 이미지의 각 pixel에 `kernel`이 적용되는 식이다.

![20200924_143556](C:\Users\multicampus\Desktop\ssafy_common\2020\team1\pixelation\image\20200924_143556.png)

- kernel 예시

### Gaussian Filter

Gaussian Filter는 Gaussian 함수를 통해 kernel 행렬 값을 수학적으로 생성하여 적용한다. 이때 kernel의 사이즈는 **양수이면서 홀수**로 지정해야한다.

```python
cv2.GaussianBlur(img, ksige, sigmaX) 
```

- ksige : (width, height) 형태의 kernel size.
- sigmaX : X 축 방향의 가우스 커널 표준 편차

## 실습1-이미지 모자이크 처리

```python
import numpy as np
import cv2

"""
가우시안 블러 적용
"""
def anonymize_face_simple(image, factor=3.0): # factor 값으로 블러 정도 조절할 수 있다 
	"""
    이미지 크기에 따라 자동으로 kernel 생성 
    """
	(h, w) = image.shape[:2] # height, width 추출 
	kW = int(w / factor)
	kH = int(h / factor)
	# 가우시안 필터에서 ksige는 양수이면서 홀수여야한다 
	if kW % 2 == 0:
		kW -= 1
	if kH % 2 == 0:
		kH -= 1
	# apply a Gaussian blur to the input image using our computed
	# kernel size
	return cv2.GaussianBlur(image, (kW, kH), 0)

"""
픽셀화하여 모자이크 만들기
"""
def anonymize_face_pixelate(image, blocks=10):
	# 이미지 N*N 개의 블럭으로 나누는 요소 만들기 
	(h, w) = image.shape[:2]
	xSteps = np.linspace(0, w, blocks + 1, dtype="int") # 0부터 w까지 blocks+1의 간격으로 요소 생성 
	ySteps = np.linspace(0, h, blocks + 1, dtype="int")
	# loop over the blocks in both the x and y direction
	for i in range(1, len(ySteps)):
		for j in range(1, len(xSteps)):
			# compute the starting and ending (x, y)-coordinates
			# 현재 블록 위치 
			startX = xSteps[j - 1]
			startY = ySteps[i - 1]
			endX = xSteps[j]
			endY = ySteps[i]
			# extract the ROI using NumPy array slicing, compute the
			# mean of the ROI, and then draw a rectangle with the
			# mean RGB values over the ROI in the original image
			roi = image[startY:endY, startX:endX]
			(B, G, R) = [int(x) for x in cv2.mean(roi)[:3]]
			cv2.rectangle(image, (startX, startY), (endX, endY),
				(B, G, R), -1)
	# return the pixelated blurred image
	return image

image = cv2.imread("../examples/captain-america.jpg") # 폴더 구조에 따라 경로 설정
blured_img = anonymize_face_simple(image)
# cv2.imshow('blured_img', blured_img);cv2.waitKey(0)
pixelated_img = anonymize_face_pixelate(blured_img)
# cv2.imshow('pixelated_img', pixelated_img);cv2.waitKey(0)
```

- 결과

  ![20200924_143950](C:\Users\multicampus\Desktop\ssafy_common\2020\team1\pixelation\image\20200924_143950.png)

## 4. 실시간 얼굴 모자이크 처리

1. 모델 불러오기

얼굴 인식을 하기 위해선 opencv에 있는 **haarcascades 파일이 필요**한데

아래 github에서 다운로드 가능하다.

https://github.com/opencv/opencv/tree/master/data/haarcascades

`haarcascade_frontalface_default.xml` 파일을 다운받아서 `haarcasecades` 폴더에 넣어주자.

1. 라이브러리 import

```python
import numpy as np
import cv2
from matplotlib import pyplot as plt
```

- 설치되지 않은 라이브러리는 `pip install` 해준다.

1. 코드

```python
from pyimagesearch.face_blurring import anonymize_face_pixelate
import numpy as np
import cv2
from matplotlib import pyplot as plt

"""
모델 불러오기
"""
xml = 'haarcascades/haarcascade_frontalface_default.xml'
face_cascade = cv2.CascadeClassifier(xml)

"""
노트북 웹캠을 카메라로 사용
"""
cap = cv2.VideoCapture(0) 
cap.set(3,640) # 너비
cap.set(4,480) # 높이

"""
영상 프레임 -> 얼굴 인식하여 ROI 추출 -> ROI에 모자이크처리(앞서 만든 함수 사용) -> 원래 프레임에 모자이크 처리된 이미지 적용
-> 위 과정 반복 
참고로 함수 없이 축소, 확대하는 방법으로도 모자이크 처리가 가능하다
"""
while(True):
    ret, frame = cap.read() # 현재 프레임 읽기
    frame = cv2.flip(frame, 1) # 좌우 대칭
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    faces = face_cascade.detectMultiScale(gray,1.05, 5) # 얼굴 인식 
    print("Number of faces detected: " + str(len(faces))) # 인식된 얼굴 개수 

    if len(faces):
        for (x,y,w,h) in faces:
            # cv2.rectangle(frame,(x,y),(x+w,y+h),(255,0,0),2) # ROI 사각형 그리기
            face_img = frame[y:y+h, x:x+w] # 인식된 얼굴 이미지 crop
            face_img = anonymize_face_pixelate(face_img)
            # face_img = cv2.resize(face_img, dsize=(0, 0), fx=0.04, fy=0.04) # 축소
            # face_img = cv2.resize(face_img, (w, h), interpolation=cv2.INTER_AREA) # 확대
            frame[y:y+h, x:x+w] = face_img # 인식된 얼굴 영역 모자이크 처리
        
    cv2.imshow('result', frame)
    
    k = cv2.waitKey(30) & 0xff
    if k == 27: # Esc 키를 누르면 종료
        break

cap.release()
cv2.destroyAllWindows()
```

- 결과

  ![20200926_130241](C:\Users\multicampus\Desktop\ssafy_common\2020\team1\pixelation\image\20200926_130241.png)

### 참고. argparse 모듈

> 인자를 입력받고, 파싱하고, 예외처리하고, 심지어 사용법(usage) 작성까지 자동으로 해주는 매우 편리한 모듈

**Command line arguments?**

- 프로그램/스크립트 런타임에 지정되는 플래그(상태 기록하고 처리 흐름 제어 위한 변수)
- 프로세스를 실행하면서 전달하는 인자
- 명령어 실행 시점마다 다른 옵션을 줄 수 있다

모듈 설치

```bash
pip install argparse
parser = argparse.ArgumentParser() # parser 생성
parser.add_argument('-i', '--id', required=True) # 입력받고자 하는 인자의 조건 설정
args = parser.parse_arge() # 파싱한 인자 반환(add_argument에서 지정한 type으로 저장됨)
args.id = ?? # 명령행으로 받은 인자 값 반환 
```

------

## 참고

[Image Smoothing - gramman 0.1 documentation](https://opencv-python.readthedocs.io/en/latest/doc/11.imageSmoothing/imageSmoothing.html)

[Blur and anonymize faces with OpenCV and Python - PyImageSearch](https://www.pyimagesearch.com/2020/04/06/blur-and-anonymize-faces-with-opencv-and-python/)