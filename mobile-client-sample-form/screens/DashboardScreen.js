import { useState } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, ScrollView,
  SafeAreaView, Alert,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';

function decodeJwt(token) {
  try {
    const payload = token.split('.')[1];
    const padded = payload.replace(/-/g, '+').replace(/_/g, '/');
    const decoded = atob(padded);
    return JSON.parse(decoded);
  } catch {
    return null;
  }
}

function formatDate(epoch) {
  if (!epoch) return '—';
  return new Date(epoch * 1000).toLocaleString();
}

function secondsUntil(epoch) {
  if (!epoch) return null;
  const diff = epoch - Math.floor(Date.now() / 1000);
  if (diff <= 0) return 'Expired';
  const m = Math.floor(diff / 60);
  const s = diff % 60;
  return m > 0 ? `${m}m ${s}s` : `${s}s`;
}

function InfoRow({ label, value, mono }) {
  return (
    <View style={styles.infoRow}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={[styles.infoValue, mono && styles.monoText]} numberOfLines={2}>
        {value ?? '—'}
      </Text>
    </View>
  );
}

function Card({ title, emoji, children }) {
  return (
    <View style={styles.card}>
      <Text style={styles.cardTitle}>{emoji}  {title}</Text>
      {children}
    </View>
  );
}

export default function DashboardScreen({ route, navigation }) {
  const { accessToken, idToken } = route.params ?? {};
  const [showRaw, setShowRaw] = useState(false);

  const claims = decodeJwt(accessToken) ?? {};
  const idClaims = idToken ? decodeJwt(idToken) : null;

  const email    = claims.email ?? idClaims?.email ?? claims.sub ?? '—';
  const name     = (claims.name ?? idClaims?.name ?? `${idClaims?.given_name ?? ''} ${idClaims?.family_name ?? ''}`.trim()) || '—';
  const orgId    = claims.org_id ?? '—';
  const roles    = Array.isArray(claims.roles) ? claims.roles : (claims.roles ? [claims.roles] : []);
  const scopes   = typeof claims.scope === 'string' ? claims.scope.split(' ') : (Array.isArray(claims.scope) ? claims.scope : []);
  const expiresAt = claims.exp;
  const issuedAt  = claims.iat;

  const initials = name !== '—'
    ? name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)
    : email.slice(0, 2).toUpperCase();

  const handleLogout = () => {
    Alert.alert('Logout', 'Are you sure you want to log out?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Logout',
        style: 'destructive',
        onPress: () => navigation.replace('Login'),
      },
    ]);
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar style="light" />

      {/* Header */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>{initials}</Text>
          </View>
          <View>
            <Text style={styles.headerName}>{name}</Text>
            <Text style={styles.headerEmail}>{email}</Text>
          </View>
        </View>
        <TouchableOpacity style={styles.logoutBtn} onPress={handleLogout}>
          <Text style={styles.logoutText}>Logout</Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.body} showsVerticalScrollIndicator={false}>

        {/* Stats row */}
        <View style={styles.statsRow}>
          <View style={styles.statBox}>
            <Text style={styles.statValue}>{roles.length || '—'}</Text>
            <Text style={styles.statLabel}>Roles</Text>
          </View>
          <View style={styles.statBox}>
            <Text style={styles.statValue}>{scopes.length || '—'}</Text>
            <Text style={styles.statLabel}>Scopes</Text>
          </View>
          <View style={[styles.statBox, { borderRightWidth: 0 }]}>
            <Text style={[styles.statValue, { color: expiresAt && secondsUntil(expiresAt) !== 'Expired' ? '#22c55e' : '#ef4444' }]}>
              {secondsUntil(expiresAt) ?? '—'}
            </Text>
            <Text style={styles.statLabel}>Token TTL</Text>
          </View>
        </View>

        {/* User info */}
        <Card title="User Profile" emoji="👤">
          <InfoRow label="Email" value={email} />
          <InfoRow label="Full name" value={name} />
          <InfoRow label="Subject (sub)" value={claims.sub} mono />
          <InfoRow label="Issuer (iss)" value={claims.iss} mono />
        </Card>

        {/* Organization */}
        <Card title="Organization" emoji="🏢">
          <InfoRow label="Org ID" value={orgId} mono />
          <InfoRow label="Client ID" value={claims.azp ?? claims.client_id ?? '—'} mono />
        </Card>

        {/* Roles & Scopes */}
        <Card title="Access" emoji="🔑">
          <Text style={styles.infoLabel}>Roles</Text>
          <View style={styles.tagRow}>
            {roles.length > 0
              ? roles.map(r => <View key={r} style={[styles.tag, styles.tagBlue]}><Text style={styles.tagText}>{r}</Text></View>)
              : <Text style={styles.emptyText}>No roles in token</Text>}
          </View>
          <Text style={[styles.infoLabel, { marginTop: 12 }]}>Scopes</Text>
          <View style={styles.tagRow}>
            {scopes.length > 0
              ? scopes.map(s => <View key={s} style={[styles.tag, styles.tagGray]}><Text style={styles.tagTextGray}>{s}</Text></View>)
              : <Text style={styles.emptyText}>No scopes</Text>}
          </View>
        </Card>

        {/* Token validity */}
        <Card title="Token" emoji="🕐">
          <InfoRow label="Issued at"  value={formatDate(issuedAt)} />
          <InfoRow label="Expires at" value={formatDate(expiresAt)} />
          <InfoRow label="Time left"  value={secondsUntil(expiresAt)} />
          <InfoRow label="Grant type" value={claims.grant_type ?? 'authorization_code'} mono />
        </Card>

        {/* Raw JWT toggle */}
        <TouchableOpacity style={styles.rawToggle} onPress={() => setShowRaw(v => !v)}>
          <Text style={styles.rawToggleText}>{showRaw ? 'Hide' : 'Show'} raw JWT claims</Text>
        </TouchableOpacity>

        {showRaw && (
          <View style={styles.rawBox}>
            <Text style={styles.rawText}>{JSON.stringify(claims, null, 2)}</Text>
          </View>
        )}

        <View style={{ height: 32 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f1f5f9',
  },
  header: {
    backgroundColor: '#1e3a5f',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    paddingTop: 20,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    flex: 1,
  },
  avatar: {
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: '#3b82f6',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '700',
  },
  headerName: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  headerEmail: {
    color: '#93c5fd',
    fontSize: 12,
  },
  logoutBtn: {
    backgroundColor: 'rgba(239,68,68,0.15)',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: 'rgba(239,68,68,0.4)',
  },
  logoutText: {
    color: '#fca5a5',
    fontSize: 13,
    fontWeight: '600',
  },
  body: {
    padding: 16,
    gap: 12,
  },
  statsRow: {
    backgroundColor: '#fff',
    borderRadius: 14,
    flexDirection: 'row',
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 6,
    elevation: 3,
  },
  statBox: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 18,
    borderRightWidth: 1,
    borderRightColor: '#f1f5f9',
  },
  statValue: {
    fontSize: 22,
    fontWeight: '700',
    color: '#1e3a5f',
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 11,
    color: '#94a3b8',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: 18,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 6,
    elevation: 3,
  },
  cardTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#1e3a5f',
    marginBottom: 14,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    paddingVertical: 7,
    borderBottomWidth: 1,
    borderBottomColor: '#f8fafc',
  },
  infoLabel: {
    fontSize: 12,
    color: '#94a3b8',
    flex: 1,
    marginTop: 2,
  },
  infoValue: {
    fontSize: 13,
    color: '#334155',
    flex: 2,
    textAlign: 'right',
  },
  monoText: {
    fontFamily: 'monospace',
    fontSize: 11,
    color: '#475569',
  },
  tagRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginTop: 6,
  },
  tag: {
    borderRadius: 20,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  tagBlue: {
    backgroundColor: '#dbeafe',
  },
  tagGray: {
    backgroundColor: '#f1f5f9',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  tagText: {
    fontSize: 12,
    color: '#1d4ed8',
    fontWeight: '600',
  },
  tagTextGray: {
    fontSize: 12,
    color: '#64748b',
  },
  emptyText: {
    fontSize: 12,
    color: '#cbd5e1',
    fontStyle: 'italic',
  },
  rawToggle: {
    alignItems: 'center',
    paddingVertical: 12,
  },
  rawToggleText: {
    color: '#3b82f6',
    fontSize: 13,
    fontWeight: '600',
  },
  rawBox: {
    backgroundColor: '#0f172a',
    borderRadius: 12,
    padding: 16,
  },
  rawText: {
    color: '#7dd3fc',
    fontSize: 11,
    fontFamily: 'monospace',
    lineHeight: 18,
  },
});
