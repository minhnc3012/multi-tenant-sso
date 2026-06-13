import { useEffect, useState } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet,
  ActivityIndicator, SafeAreaView, Platform,
} from 'react-native';
import {
  useAuthRequest,
  makeRedirectUri,
  ResponseType,
  CodeChallengeMethod,
} from 'expo-auth-session';
import * as WebBrowser from 'expo-web-browser';
import { StatusBar } from 'expo-status-bar';
import { AUTH_SERVER_URL, CLIENT_ID, SCOPES } from '../config';

// Required for Expo web: completes the auth session when the page reloads after redirect
WebBrowser.maybeCompleteAuthSession();

const discovery = {
  authorizationEndpoint: `${AUTH_SERVER_URL}/oauth2/authorize`,
  tokenEndpoint: `${AUTH_SERVER_URL}/oauth2/token`,
};

export default function LoginScreen({ navigation }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const redirectUri = makeRedirectUri({
    scheme: 'com.realestate.app',
    path: 'callback',
  });

  const [request, response, promptAsync] = useAuthRequest(
    {
      clientId: CLIENT_ID,
      redirectUri,
      scopes: SCOPES,
      responseType: ResponseType.Code,
      codeChallengeMethod: CodeChallengeMethod.S256,
    },
    discovery,
  );

  useEffect(() => {
    if (response?.type === 'success') {
      const { code } = response.params;
      exchangeCode(code, request.codeVerifier);
    } else if (response?.type === 'error') {
      setError(response.error?.message ?? 'Authorization failed');
      setLoading(false);
    } else if (response?.type === 'dismiss') {
      setLoading(false);
    }
  }, [response]);

  const exchangeCode = async (code, codeVerifier) => {
    try {
      const body = new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        redirect_uri: redirectUri,
        client_id: CLIENT_ID,
        code_verifier: codeVerifier,
      });

      const res = await fetch(`${AUTH_SERVER_URL}/oauth2/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });

      const tokens = await res.json();

      if (tokens.access_token) {
        navigation.replace('Dashboard', {
          accessToken: tokens.access_token,
          idToken: tokens.id_token,
          refreshToken: tokens.refresh_token,
        });
      } else {
        setError(tokens.error_description ?? tokens.error ?? 'Token exchange failed');
        setLoading(false);
      }
    } catch (e) {
      setError(`Network error: ${e.message}`);
      setLoading(false);
    }
  };

  const handleLogin = () => {
    setError(null);
    setLoading(true);
    promptAsync();
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar style="light" />

      <View style={styles.hero}>
        <View style={styles.logoCircle}>
          <Text style={styles.logoText}>RE</Text>
        </View>
        <Text style={styles.appName}>Real Estate Platform</Text>
        <Text style={styles.tagline}>Powered by Identity Platform</Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Welcome back</Text>
        <Text style={styles.cardSubtitle}>
          Sign in securely with your organization account.
          {'\n'}Your credentials are handled by the Identity Provider — not stored in this app.
        </Text>

        {error && (
          <View style={styles.errorBox}>
            <Text style={styles.errorText}>{error}</Text>
          </View>
        )}

        <TouchableOpacity
          style={[styles.loginBtn, (!request || loading) && styles.loginBtnDisabled]}
          onPress={handleLogin}
          disabled={!request || loading}
        >
          {loading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.loginBtnText}>Login with Identity Platform</Text>
          )}
        </TouchableOpacity>

        <View style={styles.pkceNote}>
          <Text style={styles.pkceNoteText}>OAuth2 Authorization Code + PKCE (RFC 7636)</Text>
          <Text style={styles.pkceNoteText}>No client secret stored on device</Text>
        </View>
      </View>

      <View style={styles.footer}>
        <Text style={styles.footerText}>
          {Platform.OS === 'web' ? 'Web' : Platform.OS === 'android' ? 'Android' : 'iOS'} Demo
          {' · '}Client: {CLIENT_ID}
        </Text>
        <Text style={styles.footerText}>Auth: {AUTH_SERVER_URL}</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1e3a5f',
    justifyContent: 'space-between',
    padding: 24,
  },
  hero: {
    alignItems: 'center',
    paddingTop: 48,
  },
  logoCircle: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: '#3b82f6',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 8,
  },
  logoText: {
    fontSize: 32,
    fontWeight: '800',
    color: '#fff',
    letterSpacing: 1,
  },
  appName: {
    fontSize: 26,
    fontWeight: '700',
    color: '#fff',
    marginBottom: 6,
  },
  tagline: {
    fontSize: 14,
    color: '#93c5fd',
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 20,
    padding: 28,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.2,
    shadowRadius: 16,
    elevation: 12,
  },
  cardTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: '#1e3a5f',
    marginBottom: 10,
  },
  cardSubtitle: {
    fontSize: 14,
    color: '#64748b',
    lineHeight: 22,
    marginBottom: 24,
  },
  errorBox: {
    backgroundColor: '#fef2f2',
    borderRadius: 10,
    padding: 12,
    marginBottom: 16,
    borderLeftWidth: 3,
    borderLeftColor: '#ef4444',
  },
  errorText: {
    color: '#dc2626',
    fontSize: 13,
  },
  loginBtn: {
    backgroundColor: '#3b82f6',
    borderRadius: 12,
    paddingVertical: 15,
    alignItems: 'center',
    shadowColor: '#3b82f6',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 8,
    elevation: 6,
  },
  loginBtnDisabled: {
    opacity: 0.6,
  },
  loginBtnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  pkceNote: {
    marginTop: 20,
    alignItems: 'center',
    gap: 4,
  },
  pkceNoteText: {
    fontSize: 11,
    color: '#94a3b8',
    textAlign: 'center',
  },
  footer: {
    alignItems: 'center',
    paddingBottom: 8,
    gap: 4,
  },
  footerText: {
    fontSize: 11,
    color: '#60a5fa',
    textAlign: 'center',
  },
});
