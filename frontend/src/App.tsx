import './App.css'
import { useTimerSession } from './timer/useTimerSession'
import { TimerScreen } from './timer/TimerScreen'

function App() {
  const timer = useTimerSession()
  return <TimerScreen timer={timer} />
}

export default App
