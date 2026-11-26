import { useState } from 'react'
import { money, frequencyLabel, termLabel, todayISO } from '../format'

// One insurance plan with a "Register" button.
// `recommendation` is optional: when the plan comes from the AI list we also show why it was suggested.
export default function PlanCard({ plan, recommendation, busy, onRegister }) {
  const [open, setOpen] = useState(false)
  const [startDate, setStartDate] = useState(todayISO())

  return (
    <div className="card">
      <span className="tag">{plan.policyType}</span>
      {recommendation && <span className="tag" style={{ marginLeft: 6 }}>{recommendation.suitability}</span>}
      <h3 style={{ margin: '8px 0 4px' }}>{plan.policyName}</h3>
      <p className="small">{plan.description}</p>
      {recommendation && <p><em>{recommendation.reason}</em></p>}
      <p>
        <strong>{money(plan.premiumAmount)}</strong> {frequencyLabel(plan.premiumFrequency)}<br />
        Cover {money(plan.sumInsured)} &middot; Term {termLabel(plan.termMonths)}<br />
        <span className="small">Eligible ages {plan.minEntryAge}-{plan.maxEntryAge}</span>
      </p>

      {!open && <button className="btn" onClick={() => setOpen(true)}>Register</button>}
      {open && (
        <form onSubmit={(e) => { e.preventDefault(); onRegister(plan, startDate) }}>
          <label>Policy start date
            <input type="date" value={startDate} max={todayISO()} onChange={(e) => setStartDate(e.target.value)} required />
          </label>
          <button className="btn" disabled={busy}>{busy ? 'Registering...' : 'Confirm'}</button>{' '}
          <button type="button" className="btn light" onClick={() => setOpen(false)}>Cancel</button>
        </form>
      )}
    </div>
  )
}
