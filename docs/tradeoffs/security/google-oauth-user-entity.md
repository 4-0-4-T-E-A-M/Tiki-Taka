# Google OAuth 도입에 따른 User 엔티티 변경

기존 User 엔티티는 자체 회원가입/로그인 방식을 전제로 설계되어 있었다.

기존 구조에서는 `email`, `password`, `name`이 필수값으로 존재했다.

하지만 프로젝트 인증 방식을 Google OAuth 기반으로 변경하면서, 사용자가 우리 서비스에 직접 비밀번호를 입력하지 않는 로그인 흐름이 추가되었다.

따라서 기존의 `password not null` 구조는 Google OAuth 사용자 저장 방식과 맞지 않는다는 판단을 내렸다

## 결정 사항

User 엔티티를 다음과 같이 수정한다.

- `password`를 `password_hash`로 변경한다.
- `password_hash`는 nullable로 둔다.
- 로그인 제공자를 구분하기 위해 `provider` 컬럼을 추가한다.
- 외부 OAuth 제공자의 사용자 고유 ID를 저장하기 위해 `provider_id` 컬럼을 추가한다.
- Google OAuth 사용자는 `provider = GOOGLE`, `provider_id = Google sub` 값을 가진다.
- 일반 회원가입 사용자는 `provider = LOCAL`, `password_hash` 값을 가진다.

## 3. 변경 전

```
Table users {
  user_id bigint [pk, increment]
  email varchar [not null, unique]
  password varchar [not null]
  name varchar [not null]
  created_at datetime [not null]
}
```

## 4. 변경 후

```
Enum oauth_provider {
  GOOGLE
  LOCAL
}

Table users {
  user_id bigint [pk, increment]

  email varchar [not null, unique]
  password_hash varchar

  name varchar [not null]
  profile_image_url varchar

  provider oauth_provider [not null]
  provider_id varchar

  created_at datetime [not null]
  updated_at datetime [not null]
  last_login_at datetime

  indexes {
    (email)
    (provider, provider_id) [unique]
  }
}
```

## 5. 변경 이유

Google OAuth 방식에서는 우리 서비스가 사용자의 비밀번호를 직접 관리하지 않는다.

따라서 OAuth 사용자에게는 `password_hash`가 존재하지 않을 수 있다.

하지만 추후 일반 이메일/비밀번호 로그인 방식 확장을 고려하여 `password_hash` 컬럼은 제거하지 않고 nullable로 유지한다.

또한 이메일만으로 OAuth 사용자를 식별하기보다, OAuth 제공자가 보장하는 고유 식별자인 `provider_id`를 함께 저장하는 것이 더 안전하다.

## 6. 영향 범위

- User 엔티티
- UserRepository
- 회원가입 API
- Google OAuth 로그인 로직
- JWT 발급 로직
- 인증 사용자 조회 로직
- ERD 문서