# libs

빌드하려면 이 폴더에 **`SP-Framework-1.0.0-plugin-base.jar`** 를 넣어야 한다.

## 어디서 받나

> **[daeil0102/SP-Framework](https://github.com/daeil0102/SP-Framework)**

Paper 1.17 이상은 `-base` 빌드를 쓴다. (1.12~1.16 은 `-all`)

```
libs/
└── SP-Framework-1.0.0-plugin-base.jar
```

## 왜 저장소에 없나

SP-Framework 는 **2차 배포가 금지**되어 있어서 이 저장소에 포함하지 않는다
(`.gitignore` 처리). 위 링크에서 직접 받을 것.

## 결과 jar 에는 안 들어간다

`compileOnly` 로만 쓰이므로 빌드 결과물에는 포함되지 않는다.
런타임에는 서버 `plugins/` 에 올라간 프레임워크를 쓴다.
