// AI 도우미 대화를 앱이 열려 있는 동안 메모리에만 둔다.
// - 다른 화면에 갔다가 뒤로가기로 돌아와도 대화가 남아 있다
// - 서버·localStorage·sessionStorage 에는 저장하지 않으므로 새로고침·탭 닫기·로그아웃하면 사라진다
// - 답을 기다리는 중에 화면을 떠나도 응답이 여기에 쌓이고, 돌아오면 보인다

const EMPTY = { userId: null, messages: [], busy: false, convId: 0 }
let state = EMPTY
const listeners = new Set()

export function getAssistantState() {
  return state
}

export function subscribeAssistant(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** fn(현재 상태) 가 돌려준 값을 합친다 */
export function updateAssistant(fn) {
  state = { ...state, ...fn(state) }
  listeners.forEach((l) => l())
}

/** 로그아웃·다른 계정으로 로그인·[새 대화] 때 비운다 */
export function resetAssistant(userId = null) {
  // convId 가 바뀌면 비우기 전에 보낸 질문의 늦은 응답은 버린다
  state = { ...EMPTY, userId, convId: state.convId + 1 }
  listeners.forEach((l) => l())
}
