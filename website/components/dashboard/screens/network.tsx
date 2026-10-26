"use client";

import { Building2, Check, Copy, Link2, Search, Trash2, UserPlus, X } from "lucide-react";
import { useState } from "react";
import { StatusChip } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Checkbox, Input } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { DataTable } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/endpoints";
import type { Connection, ProductMapping, SearchResult } from "@/lib/api/types";
import { date } from "@/lib/format";
import { useApi, useDashboard } from "../context";
import { PageHeader } from "../parts";

const demoNote = (mode: string) => (mode === "demo" ? " (demo — not saved)" : "");

export function NetworkScreen() {
  const { client, can } = useDashboard();
  const toast = useToast();
  const manage = can("BUSINESS_SETTINGS");
  const me = useApi(() => api.network.me(client), [client]);
  const connections = useApi(() => api.network.connections(client), [client]);
  const [q, setQ] = useState("");
  const [results, setResults] = useState<SearchResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string>();
  const [message, setMessage] = useState("");

  const search = async (e: React.FormEvent) => {
    e.preventDefault();
    if (q.trim().length < 2) return setSearchError("Type at least 2 characters");
    setSearching(true);
    setSearchError(undefined);
    try { setResults(await api.networkAdmin.search(client, q.trim())); } catch (err) { setSearchError((err as Error).message); } finally { setSearching(false); }
  };
  const act = async (fn: () => Promise<unknown>, msg: string) => {
    try { await fn(); toast(msg + demoNote(client.mode)); connections.reload(); if (results) setResults(await api.networkAdmin.search(client, q.trim()).catch(() => results)); } catch (e) { toast((e as Error).message, "error"); }
  };

  const list = connections.data ?? [];
  const incoming = list.filter((c) => c.status === "PENDING" && c.direction === "INCOMING");
  const outgoing = list.filter((c) => c.status === "PENDING" && c.direction === "OUTGOING");
  const accepted = list.filter((c) => c.status === "ACCEPTED");

  const row = (c: Connection, actions: React.ReactNode) => (
    <li key={c.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3">
      <div className="flex min-w-0 items-center gap-3">
        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-brand-50 text-brand-600"><Building2 className="size-4" aria-hidden /></span>
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{c.business.name}</p>
          <p className="text-xs text-muted">{[c.business.businessType, c.business.city, c.business.businessCode].filter(Boolean).join(" · ")}{c.message ? ` · “${c.message}”` : ""}</p>
        </div>
      </div>
      <div className="flex gap-2">{actions}</div>
    </li>
  );

  return (
    <>
      <PageHeader title="Business network" description="Connect with businesses you buy from or sell to, then exchange B2B invoices with them." />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          {incoming.length > 0 && (
            <Card>
              <CardHeader title="Requests to you" description="Accepting lets both businesses send each other invoices." />
              <ul className="divide-y divide-line">{incoming.map((c) => row(c, manage ? <>
                <Button size="sm" icon={<Check className="size-4" aria-hidden />} onClick={() => act(() => api.networkAdmin.respond(client, c.id, "accept"), `Connected with ${c.business.name}`)}>Accept</Button>
                <Button size="sm" variant="secondary" icon={<X className="size-4" aria-hidden />} onClick={() => act(() => api.networkAdmin.respond(client, c.id, "reject"), "Request rejected")}>Reject</Button>
              </> : <span className="text-xs text-muted">An admin can accept this</span>))}</ul>
            </Card>
          )}
          <Card>
            <CardHeader title="Connected businesses" description={`${accepted.length} connection${accepted.length === 1 ? "" : "s"}`} />
            {connections.error ? <ErrorState error={connections.error} onRetry={connections.reload} /> : !connections.data ? <LoadingBlock rows={3} /> : accepted.length === 0 ? <EmptyState icon={Building2} title="No connected businesses yet" description="Search for a business by name, business code or phone number." /> : (
              <ul className="divide-y divide-line">{accepted.map((c) => row(c, <>
                <span className="text-xs text-muted">Since {date(c.respondedAt ?? c.createdAt)}</span>
                {manage && <Button size="sm" variant="ghost" onClick={() => act(() => api.networkAdmin.respond(client, c.id, "remove"), "Connection removed")}>Remove</Button>}
              </>))}</ul>
            )}
          </Card>
          {outgoing.length > 0 && (
            <Card>
              <CardHeader title="Waiting for a reply" />
              <ul className="divide-y divide-line">{outgoing.map((c) => row(c, <><StatusChip status="PENDING" />{manage && <Button size="sm" variant="ghost" onClick={() => act(() => api.networkAdmin.respond(client, c.id, "remove"), "Request withdrawn")}>Withdraw</Button>}</>))}</ul>
            </Card>
          )}
          <Card>
            <CardHeader title="Find a business" description="Search shows only name, type, city and business code." />
            <CardBody className="space-y-4">
              <form onSubmit={search} className="flex flex-col gap-2 sm:flex-row" role="search">
                <label htmlFor="net-q" className="sr-only">Business name, code or phone</label>
                <Input id="net-q" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Name, business code (e.g. B7K3F9QA) or exact phone" />
                <Button type="submit" loading={searching} icon={<Search className="size-4" aria-hidden />}>Search</Button>
              </form>
              {searchError && <p className="text-sm font-medium text-danger">{searchError}</p>}
              {manage && <Input aria-label="Message with your request" value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Optional message with your request" maxLength={300} />}
              {results && (results.length === 0 ? <p className="text-sm text-muted">No businesses found.</p> : (
                <ul className="divide-y divide-line rounded-lg border border-line">
                  {results.map((r) => (
                    <li key={r.business.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                      <div>
                        <p className="text-sm font-medium">{r.business.name}</p>
                        <p className="text-xs text-muted">{[r.business.businessType, r.business.city, r.business.businessCode].filter(Boolean).join(" · ")}</p>
                      </div>
                      {r.connectionStatus === "ACCEPTED" ? <StatusChip status="ACCEPTED" /> : r.connectionStatus === "PENDING" ? (
                        r.direction === "INCOMING" && manage ? <Button size="sm" onClick={() => act(() => api.networkAdmin.respond(client, r.connectionId!, "accept"), `Connected with ${r.business.name}`)}>Accept their request</Button> : <StatusChip status="PENDING" />
                      ) : manage ? (
                        <Button size="sm" variant="secondary" icon={<UserPlus className="size-4" aria-hidden />} onClick={() => act(() => api.networkAdmin.connect(client, r.business.id, message || undefined), "Connection request sent")}>Connect</Button>
                      ) : <span className="text-xs text-muted">An admin can connect</span>}
                    </li>
                  ))}
                </ul>
              ))}
              {!manage && <Notice>Sending and accepting connection requests needs the “Business settings” permission.</Notice>}
            </CardBody>
          </Card>
        </div>
        <Card className="h-fit">
          <CardHeader title="Your business code" />
          <CardBody className="space-y-4">
            {me.error ? <ErrorState error={me.error} onRetry={me.reload} /> : !me.data ? <LoadingBlock rows={2} /> : (
              <>
                <div className="flex items-center justify-between gap-2 rounded-lg bg-canvas px-4 py-3">
                  <span className="font-mono text-lg font-semibold tracking-widest">{me.data.businessCode}</span>
                  <Button size="sm" variant="ghost" icon={<Copy className="size-4" aria-hidden />} onClick={() => { navigator.clipboard?.writeText(me.data!.businessCode); toast("Business code copied"); }}>Copy</Button>
                </div>
                <p className="text-sm text-muted">Share this code so other businesses can find and connect with you.</p>
                <Checkbox
                  label="Show my business in search"
                  description="When off, others can only find you by your exact code"
                  checked={me.data.discoverable}
                  disabled={!manage}
                  onChange={async (e) => { try { me.setData(await api.networkAdmin.setDiscoverable(client, e.target.checked)); toast("Visibility updated" + demoNote(client.mode)); } catch (err) { toast((err as Error).message, "error"); } }}
                />
              </>
            )}
          </CardBody>
        </Card>
      </div>
    </>
  );
}

export function ProductLinksScreen() {
  const { client, href, can } = useDashboard();
  const toast = useToast();
  const links = useApi(() => (can("PURCHASE_MANAGE") ? api.tradeAdmin.mappings(client) : Promise.resolve([])), [client]);
  if (!can("PURCHASE_MANAGE")) return <><PageHeader title="Product links" /><Notice tone="warning">You need the “Record purchases” permission.</Notice></>;
  return (
    <>
      <PageHeader title="Product links" description="When you approve a supplier's invoice, each of their products is remembered against one of yours. Next time it is matched automatically." />
      <Card>
        {links.error ? <ErrorState error={links.error} onRetry={links.reload} /> : !links.data ? <LoadingBlock /> : links.data.length === 0 ? <EmptyState icon={Link2} title="No product links yet" description="Links are created when you approve invoices from suppliers." /> : (
          <DataTable<ProductMapping> caption="Product links" rows={links.data} rowKey={(m) => m.id} columns={[
            { header: "Supplier", cell: (m) => m.seller.name },
            { header: "Their product", cell: (m) => m.sellerProductName },
            { header: "Your product", cell: (m) => <a href={href(`/products/${m.buyerProductId}`)} className="font-medium text-brand-700 hover:underline">{m.buyerProductName}{m.buyerVariantName && m.buyerVariantName !== m.buyerProductName ? ` · ${m.buyerVariantName}` : ""}</a> },
            { header: "Updated", cell: (m) => date(m.updatedAt), hideOnMobile: true },
            { header: "Actions", align: "right", cell: (m) => <button type="button" className="rounded-md p-1.5 text-muted hover:bg-danger-bg hover:text-danger" aria-label={`Remove link for ${m.sellerProductName}`} onClick={async () => { try { await api.tradeAdmin.deleteMapping(client, m.id); toast("Link removed" + demoNote(client.mode)); links.reload(); } catch (e) { toast((e as Error).message, "error"); } }}><Trash2 className="size-4" aria-hidden /></button> },
          ]} />
        )}
      </Card>
    </>
  );
}
