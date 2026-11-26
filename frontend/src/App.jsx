import { useEffect, useState } from 'react'
import api, { messageFromError } from './api'
import { firstName } from './format'
import Message from './components/Message'
import Login from './pages/Login'
import MyPolicies from './pages/MyPolicies'
import Plans from './pages/Plans'
import Recommendations from './pages/Recommendations'
import Payments from './pages/Payments'
import Search from './pages/Search'

const TABS = [
  { id: 'policies', label: 'My policies' },
  { id: 'plans', label: 'Plans' },
  { id: 'recommend', label: 'Recommended (AI)' },
  { id: 'payments', label: 'Payments' },
  { id: 'search', label: 'Search' },
]

export default function App() {
  const [customer, setCustomer] = useState(null)
  const [checking, setChecking] = useState(!!localStorage.getItem('healthdekho-token'))
  const [tab, setTab] = useState('policies')
  const [policies, setPolicies] = useState([])
  const [availablePlans, setAvailablePlans] = useState([])   // not yet registered by this customer
  const [allPlans, setAllPlans] = useState([])
  const [message, setMessage] = useState(null)
  const [busy, setBusy] = useState(false)

  // On page load: if we have a saved token, find out who is logged in.
  useEffect(() => {
    if (!checking) return
    api.get('/auth/me')
      .then(({ data }) => setCustomer(data))
      .catch(() => localStorage.removeItem('healthdekho-token'))
      .finally(() => setChecking(false))
  }, [checking])

  // api.js fires this event when the token has expired.
  useEffect(() => {
    const onExpired = () => { setCustomer(null); setMessage({ type: 'error', text: 'Your session expired. Please sign in again.' }) }
    window.addEventListener('healthdekho:session-expired', onExpired)
    return () => window.removeEventListener('healthdekho:session-expired', onExpired)
  }, [])

  // Once logged in, load the data every page needs.
  useEffect(() => { if (customer) loadData() }, [customer])

  async function loadData() {
    try {
      const [mine, available, all] = await Promise.all([api.get('/policies'), api.get('/products/available'), api.get('/products')])
      setPolicies(mine.data)
      setAvailablePlans(available.data)
      setAllPlans(all.data)
    } catch (error) {
      setMessage({ type: 'error', text: messageFromError(error) })
    }
  }

  function handleLogin(token, profile) {
    localStorage.setItem('healthdekho-token', token)
    setMessage(null)
    setCustomer(profile)
  }

  function logout() {
    localStorage.removeItem('healthdekho-token')
    setCustomer(null)
    setPolicies([])
  }

  async function registerPolicy(plan, startDate, recommendationRequestId) {
    setBusy(true)
    try {
      const { data } = await api.post('/policies', { productCode: plan.productCode, startDate, recommendationRequestId })
      await loadData()
      setMessage({ type: 'success', text: `Policy ${data.policyNumber} is now active.` })
      setTab('policies')
    } catch (error) {
      setMessage({ type: 'error', text: messageFromError(error) })
    } finally {
      setBusy(false)
    }
  }

  if (checking) return <p className="page">Loading...</p>
  if (!customer) return <><Message message={message} /><Login onLogin={handleLogin} /></>

  return (
    <>
      <div className="topbar">
        <span className="brand">HealthDekho</span>
        <span>Hi, {firstName(customer.fullName)} &nbsp; <button className="btn light" onClick={logout}>Log out</button></span>
      </div>
      <div className="tabs">
        {TABS.map((t) => (
          <button key={t.id} className={`tab ${tab === t.id ? 'active' : ''}`} onClick={() => { setTab(t.id); setMessage(null) }}>{t.label}</button>
        ))}
      </div>

      <div className="page">
        <Message message={message} />
        {tab === 'policies' && <MyPolicies policies={policies} />}
        {tab === 'plans' && <Plans plans={availablePlans} busy={busy} onRegister={registerPolicy} />}
        {tab === 'recommend' && <Recommendations allPlans={allPlans} busy={busy} onRegister={registerPolicy} />}
        {tab === 'payments' && <Payments policies={policies} onPaid={loadData} />}
        {tab === 'search' && <Search />}
      </div>
    </>
  )
}
