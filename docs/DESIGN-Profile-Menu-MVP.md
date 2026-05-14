# Profile Menu MVP Design

# Intent
- 프로필 메뉴는 별도 설정 화면이 아니라 현재 화면 위에 잠깐 열리는 오른쪽 계정 패널이다.
- 기존 앱의 밝은 배경, 20dp 화면 여백, 32dp profile initial, Material 3 밀도를 유지한다.

# Entry
- Todo, Calendar, Friends 상단 프로필 원형을 버튼으로 사용한다.
- 시각 크기는 32dp 원형을 유지하고 터치 영역은 48dp로 확장한다.
- profile initial이 있으면 첫 글자, 없으면 person icon을 표시한다.

# Drawer
- 전체 화면 scrim 위에 오른쪽에서 slide-in 한다.
- scrim은 black 28% alpha를 사용한다.
- drawer는 compact에서 화면 너비의 약 86%, 최대 360dp를 사용한다.
- 왼쪽 상단/하단 모서리는 24dp 라운드 처리한다.
- status/navigation bar inset을 반영한다.

# Layout
- 상단: `Account/계정` 제목과 close icon.
- 프로필 요약: 48dp avatar, 닉네임, 이메일.
- 빠른 액션: 닉네임 복사, 알림 설정, 앱 설정.
- 정보: 개인정보 처리방침, 서비스 이용약관, 앱 버전.
- 하단: destructive color의 로그아웃.

# Interaction
- 닫기 버튼, scrim tap, system back으로 닫는다.
- 닉네임 복사는 snackbar로 완료를 알린다.
- Privacy/Terms URL이 없으면 준비 중 snackbar를 보여준다.
- 로그아웃은 `AlertDialog`에서 한 번 더 확인한다.
- 진행 중에는 confirm 버튼을 비활성화하고 작은 progress indicator를 보여준다.

# Accessibility
- 모든 row는 최소 56dp 높이를 가진다.
- 버튼 역할은 텍스트 라벨 또는 content description으로 전달한다.
- 로그아웃은 색상뿐 아니라 텍스트와 확인 다이얼로그로 destructive 액션임을 전달한다.
