"use client";

import { Archive, ArchiveRestore, Pencil, Plus, Users } from "lucide-react";
import { useState } from "react";
import { StatusChip } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { DataTable } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/client";
import { api, type PartyKind } from "@/lib/api/endpoints";
import type { Party, PartyRequest } from "@/lib/api/types";
import { date } from "@/lib/format";
import { fieldErrorsOf, partySchema } from "@/lib/validation";
import { useApi, useDashboard, useDebounced } from "../context";
import { PageHeader, SearchBox } from "../parts";

export function PartiesScreen({ kind }: { kind: PartyKind }) {
  const { client, can } = useDashboard();
  const toast = useToast();
  const singular = kind === "customers" ? "customer" : "supplier";
  const canManage = kind === "customers" ? can("SALE_RECORD") : can("PURCHASE_MANAGE");
  const [q, setQ] = useState("");
  const dq = useDebounced(q);
  const [showArchived, setShowArchived] = useState(false);
  const list = useApi(() => api.parties.list(client, kind, { q: dq, includeArchived: showArchived }), [client, kind, dq, showArchived]);
  const [editing, setEditing] = useState<Party | "new" | null>(null);

  const toggle = async (p: Party) => {
    try {
      if (p.archived) await api.parties.restore(client, kind, p.id);
      else await api.parties.archive(client, kind, p.id);
      toast(p.archived ? `Restored ${p.name}` : `Archived ${p.name}`);
      list.reload();
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  return (
    <>
      <PageHeader
        title={kind === "customers" ? "Customers" : "Suppliers"}
        description={kind === "customers" ? "People and businesses you sell to." : "Businesses you buy from."}
        actions={canManage && <Button icon={<Plus className="size-4" aria-hidden />} onClick={() => setEditing("new")}>Add {singular}</Button>}
      />
      <Card>
        <div className="flex flex-col gap-3 border-b border-line p-4 sm:flex-row">
          <SearchBox value={q} onChange={setQ} placeholder="Search name, phone or email" label={`Search ${kind}`} />
          <label htmlFor="party-arch" className="sr-only">Show</label>
          <Select id="party-arch" value={showArchived ? "1" : ""} onChange={(e) => setShowArchived(e.target.value === "1")} className="sm:ml-auto sm:w-48">
            <option value="">Active only</option>
            <option value="1">Include archived</option>
          </Select>
        </div>
        {list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : !list.data ? <LoadingBlock rows={5} /> : list.data.length === 0 ? (
          <EmptyState icon={Users} title={dq ? `No ${kind} match “${dq}”` : `No ${kind} yet`} description={kind === "customers" ? "Customers can also be saved while recording a sale." : "Suppliers can also be saved while recording a purchase."} />
        ) : (
          <DataTable<Party>
            caption={kind}
            rows={list.data}
            rowKey={(p) => p.id}
            columns={[
              { header: "Name", cell: (p) => <span className="font-medium">{p.name}</span> },
              { header: "Phone", cell: (p) => p.phone ?? "—" },
              { header: "Email", cell: (p) => p.email ?? "—", hideOnMobile: true },
              ...(kind === "suppliers" ? [{ header: "Tax ID", cell: (p: Party) => p.taxId ?? "—", hideOnMobile: true }] : []),
              { header: "Added", cell: (p) => date(p.createdAt), hideOnMobile: true },
              { header: "Status", cell: (p) => <StatusChip status={p.archived ? "ARCHIVED" : "ACTIVE"} /> },
              ...(canManage
                ? [{
                    header: "Actions",
                    align: "right" as const,
                    cell: (p: Party) => (
                      <div className="flex justify-end gap-1">
                        <button type="button" onClick={() => setEditing(p)} className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={`Edit ${p.name}`}><Pencil className="size-4" aria-hidden /></button>
                        <button type="button" onClick={() => toggle(p)} className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={p.archived ? `Restore ${p.name}` : `Archive ${p.name}`}>
                          {p.archived ? <ArchiveRestore className="size-4" aria-hidden /> : <Archive className="size-4" aria-hidden />}
                        </button>
                      </div>
                    ),
                  }]
                : []),
            ]}
          />
        )}
      </Card>
      <PartyDialog kind={kind} party={editing} onClose={() => setEditing(null)} onSaved={() => list.reload()} />
    </>
  );
}

function PartyDialog({ kind, party, onClose, onSaved }: { kind: PartyKind; party: Party | "new" | null; onClose: () => void; onSaved: () => void }) {
  const { client } = useDashboard();
  const toast = useToast();
  const singular = kind === "customers" ? "customer" : "supplier";
  const existing = party && party !== "new" ? party : null;
  const [f, setF] = useState({ name: "", phone: "", email: "", address: "", taxId: "", notes: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [lastParty, setLastParty] = useState<typeof party>(null);

  if (party !== lastParty) {
    setLastParty(party);
    setErrors({});
    setFormError(undefined);
    setF({ name: existing?.name ?? "", phone: existing?.phone ?? "", email: existing?.email ?? "", address: existing?.address ?? "", taxId: existing?.taxId ?? "", notes: existing?.notes ?? "" });
  }
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = partySchema.safeParse(f);
    if (!parsed.success) return setErrors(fieldErrorsOf(parsed.error));
    const body = Object.fromEntries(Object.entries(parsed.data).map(([k, v]) => [k, v === "" ? undefined : v])) as unknown as PartyRequest;
    if (kind === "customers") delete body.taxId;
    setSaving(true);
    setFormError(undefined);
    try {
      if (existing) await api.parties.update(client, kind, existing.id, body);
      else await api.parties.create(client, kind, body);
      toast(`${existing ? "Updated" : "Added"} ${body.name}${client.mode === "demo" ? " (demo — not saved)" : ""}`);
      onSaved();
      onClose();
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        setFormError(err.message);
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog
      open={party !== null}
      onClose={onClose}
      title={existing ? `Edit ${existing.name}` : `Add ${singular}`}
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="party-form" loading={saving}>Save</Button></>}
    >
      <form id="party-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        {formError && <Notice tone="danger" className="sm:col-span-2">{formError}</Notice>}
        <Field label="Name" required error={errors.name} className="sm:col-span-2">{(p) => <Input {...p} value={f.name} onChange={set("name")} maxLength={200} />}</Field>
        <Field label="Phone" error={errors.phone}>{(p) => <Input {...p} type="tel" value={f.phone} onChange={set("phone")} maxLength={50} />}</Field>
        <Field label="Email" error={errors.email}>{(p) => <Input {...p} type="email" value={f.email} onChange={set("email")} maxLength={200} />}</Field>
        <Field label="Address" error={errors.address} className="sm:col-span-2">{(p) => <Input {...p} value={f.address} onChange={set("address")} maxLength={500} />}</Field>
        {kind === "suppliers" && <Field label="Tax ID (e.g. GSTIN)" error={errors.taxId}>{(p) => <Input {...p} value={f.taxId} onChange={set("taxId")} maxLength={50} />}</Field>}
        <Field label="Notes" error={errors.notes} className="sm:col-span-2">{(p) => <Textarea {...p} value={f.notes} onChange={set("notes")} maxLength={1000} className="min-h-16" />}</Field>
      </form>
    </Dialog>
  );
}
