# libs

여기에 **`SP-Framework-1.0.0-plugin-base.jar`** 를 넣어야 빌드된다.

저장소에는 올리지 않는다(`.gitignore`). 팀에서 배포본을 받아 이 폴더에 복사할 것.

```
libs/
└── SP-Framework-1.0.0-plugin-base.jar
```

`compileOnly` 로만 쓰이므로 결과 jar 에는 포함되지 않는다.
런타임에는 서버의 `plugins/` 에 올라간 프레임워크를 쓴다.
