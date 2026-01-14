/**
 * App Navigation Setup
 */

import React from 'react';
import {NavigationContainer} from '@react-navigation/native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import {useAuth} from '../context/AuthContext';
import {
  LoginScreen,
  HomeScreen,
  PendingJourneysScreen,
  PendingJourneyDetailScreen,
  ValidatedJourneysScreen,
} from '../screens';
import {ActivityIndicator, View, StyleSheet} from 'react-native';

// Define navigation types
export type RootStackParamList = {
  Login: undefined;
  Home: undefined;
  PendingJourneys: undefined;
  PendingJourneyDetail: {journeyId: number};
  ValidatedJourneys: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

// Screen options
const screenOptions = {
  headerStyle: {
    backgroundColor: '#2E7D32',
  },
  headerTintColor: '#fff',
  headerTitleStyle: {
    fontWeight: '600' as const,
  },
};

function AuthStack() {
  return (
    <Stack.Navigator screenOptions={{headerShown: false}}>
      <Stack.Screen name="Login" component={LoginScreen} />
    </Stack.Navigator>
  );
}

function MainStack() {
  return (
    <Stack.Navigator screenOptions={screenOptions}>
      <Stack.Screen
        name="Home"
        component={HomeScreen}
        options={{title: 'Green Mobility Pass'}}
      />
      <Stack.Screen
        name="PendingJourneys"
        component={PendingJourneysScreen}
        options={{title: 'Trajets en attente'}}
      />
      <Stack.Screen
        name="PendingJourneyDetail"
        component={PendingJourneyDetailScreen}
        options={{title: 'Details du trajet'}}
      />
      <Stack.Screen
        name="ValidatedJourneys"
        component={ValidatedJourneysScreen}
        options={{title: 'Trajets valides'}}
      />
    </Stack.Navigator>
  );
}

export default function AppNavigator(): JSX.Element {
  const {isAuthenticated, isLoading} = useAuth();

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#2E7D32" />
      </View>
    );
  }

  return (
    <NavigationContainer>
      {isAuthenticated ? <MainStack /> : <AuthStack />}
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
  },
});
