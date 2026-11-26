import { useState } from 'react'
import api, { messageFromError } from '../api'
import PlanCard from '../components/PlanCard'
import Message from '../components/Message'

// Asks the backend (Spring AI + Amazon Bedrock) for plans that suit the customer.
export default function Recommendations({ allPlans, busy, onRegister }) {
  const [form, setForm] = useState({ goals: '', preferredTerm: '', preferredPolicyType: '' })
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState(null)

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  async function submit(event) {
    event.preventDefault()
    setLoading(true)
    setMessage(null)
    try {
      const { data } = await api.post('/recommendations', form)
      setResult(data)
    } catch (error) {
      setMessage({ type: 'error', text: messageFromError(error) })
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <h1>Recommended for you</h1>
      <p className="muted">Tell us what you need. Suggestions are guidance, not financial advice.</p>
      <Message message={message} />

      <form className="card" onSubmit={submit}>
        <label>What are you looking for?
          <textarea rows="3" maxLength="1000" value={form.goals} onChange={update('goals')}
                    placeholder="For example: health cover for my family" />
        </label>
        <div className="row">
          <label>Policy type
            <select value={form.preferredPolicyType} onChange={update('preferredPolicyType')}>
              <option value="">No preference</option>
              <option value="HEALTH">Health</option>
              <option value="TERM">Term life</option>
              <option value="SAVINGS">Savings</option>
              <option value="ACCIDENT">Personal accident</option>
            </select>
          </label>
          <label>Preferred term
            <input maxLength="120" value={form.preferredTerm} onChange={update('preferredTerm')} placeholder="e.g. 10 years" />
          </label>
        </div>
        <button className="btn" disabled={loading}>{loading ? 'Thinking...' : 'Get recommendations'}</button>
      </form>

      {result && (
        <>
          <p className="small">
            {result.recommendations.length} suggestion(s) &middot; source: {result.modelName}
          </p>
          <div className="grid">
            {result.recommendations.map((item) => {
              const plan = allPlans.find((p) => p.productCode === item.productCode) || item
              return (
                <PlanCard key={item.productCode} plan={plan} recommendation={item} busy={busy}
                          onRegister={(p, date) => onRegister(p, date, result.requestId)} />
              )
            })}
          </div>
        </>
      )}
    </>
  )
}
