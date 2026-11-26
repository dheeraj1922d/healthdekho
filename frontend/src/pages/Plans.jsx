import PlanCard from '../components/PlanCard'

// Plans the customer has not registered yet.
export default function Plans({ plans, busy, onRegister }) {
  return (
    <>
      <h1>Plans</h1>
      <p className="muted">Plans you have not registered yet.</p>
      {plans.length === 0 && <div className="card">You have registered every available plan.</div>}
      <div className="grid">
        {plans.map((plan) => (
          <PlanCard key={plan.productCode} plan={plan} busy={busy} onRegister={(p, date) => onRegister(p, date, null)} />
        ))}
      </div>
    </>
  )
}
