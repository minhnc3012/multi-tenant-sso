import { Platform } from 'react-native';

// Android emulator: use 10.0.2.2 to reach host machine's localhost.
// Physical device: replace with your machine's LAN IP, e.g. 'http://192.168.1.10:8080'
export const AUTH_SERVER_URL = Platform.OS === 'android'
  ? 'http://10.0.2.2:8080'
  : 'http://localhost:8080';

export const CLIENT_ID = 'realestate-mobile-client';
export const SCOPES = ['openid', 'profile', 'email'];
