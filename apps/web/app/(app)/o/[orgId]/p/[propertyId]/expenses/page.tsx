import {
  createExpenseAction,
  createExpenseTypeAction,
  deleteExpenseAction,
} from "@/app/(app)/insight-actions";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import {
  listExpenseTypes,
  listExpenses,
} from "@/lib/dwellio-api/expenses";
import { formatDate, formatMoney } from "@/lib/format";

export default async function ExpensesPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const token = await requireAccessToken();
  const today = new Date().toISOString().slice(0, 10);

  try {
    const [types, expenses] = await Promise.all([
      listExpenseTypes(token, propertyId),
      listExpenses(token, propertyId),
    ]);
    const typeName = new Map(types.map((item) => [item.id, item.name]));

    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Expenses</h1>

        {expenses.length === 0 ? (
          <EmptyState title="No expenses" />
        ) : (
          <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
            {expenses.map((expense) => (
              <li
                key={expense.id}
                className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
              >
                <div>
                  <div className="font-medium">
                    {typeName.get(expense.expenseTypeId) ?? "Expense"} ·{" "}
                    {formatMoney(expense.amount, expense.currency)}
                  </div>
                  <div className="text-sm text-neutral-600">
                    {formatDate(expense.incurredOn)}
                    {expense.notes ? ` · ${expense.notes}` : ""}
                  </div>
                </div>
                <form action={deleteExpenseAction}>
                  <input type="hidden" name="orgId" value={orgId} />
                  <input type="hidden" name="propertyId" value={propertyId} />
                  <input type="hidden" name="expenseId" value={expense.id} />
                  <Button type="submit" variant="danger">
                    Delete
                  </Button>
                </form>
              </li>
            ))}
          </ul>
        )}

        <section className="grid gap-4 lg:grid-cols-2">
          <form
            action={createExpenseTypeAction}
            className="space-y-3 rounded-lg border border-neutral-200 bg-white p-4"
          >
            <h2 className="text-lg font-semibold">Add expense type</h2>
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <Input label="Name" name="name" required />
            <Button type="submit">Create type</Button>
          </form>

          <form
            action={createExpenseAction}
            className="space-y-3 rounded-lg border border-neutral-200 bg-white p-4"
          >
            <h2 className="text-lg font-semibold">Add expense</h2>
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <Select label="Type" name="expenseTypeId" required defaultValue="">
              <option value="" disabled>
                Select type
              </option>
              {types
                .filter((item) => item.active)
                .map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
            </Select>
            <Input
              label="Amount"
              name="amount"
              type="number"
              step="0.01"
              min="0.01"
              required
            />
            <Input
              label="Incurred on"
              name="incurredOn"
              type="date"
              defaultValue={today}
              required
            />
            <Input label="Notes" name="notes" />
            <Button type="submit">Create expense</Button>
          </form>
        </section>
      </div>
    );
  } catch (error) {
    if (error instanceof DwellioApiError) {
      return (
        <Alert tone="error" requestId={error.requestId}>
          {error.message}
        </Alert>
      );
    }
    throw error;
  }
}
