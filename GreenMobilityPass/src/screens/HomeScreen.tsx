/**
 * Home Screen - Detection controls and statistics
 */

import React, {useState, useEffect, useCallback} from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
  ScrollView,
  RefreshControl,
} from 'react-native';
import {useNavigation, useFocusEffect} from '@react-navigation/native';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {useAuth} from '../context/AuthContext';
import {apiClient} from '../api/client';
import tripDetection from '../native/TripDetection';
import {UserStatistics, LocalJourney} from '../api/types';

type RootStackParamList = {
  Home: undefined;
  PendingJourneys: undefined;
  ValidatedJourneys: undefined;
};

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'Home'>;

export default function HomeScreen(): JSX.Element {
  const navigation = useNavigation<NavigationProp>();
  const {user, logout} = useAuth();

  const [isDetectionRunning, setIsDetectionRunning] = useState(false);
  const [pendingCount, setPendingCount] = useState(0);
  const [statistics, setStatistics] = useState<UserStatistics | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  // Load data on focus
  useFocusEffect(
    useCallback(() => {
      loadData();
    }, []),
  );

  // Set up event listeners
  useEffect(() => {
    const unsubscribeTripDetected = tripDetection.addTripDetectedListener(
      (journey: LocalJourney) => {
        Alert.alert(
          'Nouveau trajet detecte!',
          `Mode: ${getTransportLabel(journey.detectedTransportType)}\n` +
            `Duree: ${journey.durationMinutes} min\n` +
            `Distance: ${journey.distanceKm.toFixed(1)} km`,
        );
        loadPendingCount();
      },
    );

    const unsubscribeStateChange = tripDetection.addStateChangeListener(
      state => {
        setIsDetectionRunning(state.isRunning);
      },
    );

    return () => {
      unsubscribeTripDetected?.();
      unsubscribeStateChange?.();
    };
  }, []);

  const loadData = async () => {
    setIsLoading(true);
    try {
      await Promise.all([
        loadDetectionStatus(),
        loadPendingCount(),
        loadStatistics(),
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  const loadDetectionStatus = async () => {
    try {
      const running = await tripDetection.isDetectionRunning();
      setIsDetectionRunning(running);
    } catch (error) {
      console.error('Failed to check detection status:', error);
    }
  };

  const loadPendingCount = async () => {
    try {
      const count = await tripDetection.getPendingCount();
      setPendingCount(count);
    } catch (error) {
      console.error('Failed to get pending count:', error);
    }
  };

  const loadStatistics = async () => {
    try {
      const stats = await apiClient.getStatistics();
      setStatistics(stats);
    } catch (error) {
      console.error('Failed to load statistics:', error);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadData();
    setRefreshing(false);
  };

  const handleStartDetection = async () => {
    try {
      // Check permissions first
      const permissions = await tripDetection.checkPermissions();
      if (!permissions.allGranted) {
        await tripDetection.requestPermissions();
        // Check again after request
        const newPermissions = await tripDetection.checkPermissions();
        if (!newPermissions.allGranted) {
          Alert.alert(
            'Permissions requises',
            'Veuillez accorder les permissions necessaires dans les parametres.',
          );
          return;
        }
      }

      await tripDetection.startDetection();
      setIsDetectionRunning(true);
      Alert.alert('Detection activee', 'La detection de trajets est maintenant active.');
    } catch (error) {
      const message =
        error instanceof Error ? error.message : 'Erreur inconnue';
      Alert.alert('Erreur', message);
    }
  };

  const handleStopDetection = async () => {
    try {
      await tripDetection.stopDetection();
      setIsDetectionRunning(false);
      Alert.alert('Detection desactivee', 'La detection de trajets a ete arretee.');
    } catch (error) {
      const message =
        error instanceof Error ? error.message : 'Erreur inconnue';
      Alert.alert('Erreur', message);
    }
  };

  const handleLogout = async () => {
    Alert.alert('Deconnexion', 'Voulez-vous vous deconnecter?', [
      {text: 'Annuler', style: 'cancel'},
      {
        text: 'Deconnecter',
        style: 'destructive',
        onPress: async () => {
          if (isDetectionRunning) {
            await tripDetection.stopDetection();
          }
          await logout();
        },
      },
    ]);
  };

  const getTransportLabel = (type: string): string => {
    const labels: Record<string, string> = {
      apied: 'A pied',
      velo: 'Velo',
      voiture: 'Voiture',
      transport_commun: 'Transport en commun',
    };
    return labels[type] || type;
  };

  return (
    <ScrollView
      style={styles.container}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
      }>
      {/* Header */}
      <View style={styles.header}>
        <View>
          <Text style={styles.welcomeText}>Bienvenue,</Text>
          <Text style={styles.username}>{user?.username || 'Utilisateur'}</Text>
        </View>
        <TouchableOpacity onPress={handleLogout} style={styles.logoutButton}>
          <Text style={styles.logoutText}>Deconnexion</Text>
        </TouchableOpacity>
      </View>

      {/* Detection Control */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Detection de trajets</Text>
        <View style={styles.detectionCard}>
          <View style={styles.statusRow}>
            <View
              style={[
                styles.statusDot,
                isDetectionRunning ? styles.statusActive : styles.statusInactive,
              ]}
            />
            <Text style={styles.statusText}>
              {isDetectionRunning ? 'Active' : 'Inactive'}
            </Text>
          </View>

          {isDetectionRunning ? (
            <TouchableOpacity
              style={[styles.button, styles.stopButton]}
              onPress={handleStopDetection}>
              <Text style={styles.buttonText}>Arreter la detection</Text>
            </TouchableOpacity>
          ) : (
            <TouchableOpacity
              style={[styles.button, styles.startButton]}
              onPress={handleStartDetection}>
              <Text style={styles.buttonText}>Demarrer la detection</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>

      {/* Pending Journeys */}
      <TouchableOpacity
        style={styles.section}
        onPress={() => navigation.navigate('PendingJourneys')}>
        <View style={styles.pendingCard}>
          <Text style={styles.pendingCount}>{pendingCount}</Text>
          <Text style={styles.pendingLabel}>
            Trajet{pendingCount !== 1 ? 's' : ''} en attente
          </Text>
          <Text style={styles.pendingHint}>Appuyez pour voir et valider</Text>
        </View>
      </TouchableOpacity>

      {/* Statistics */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Mes statistiques</Text>
        {isLoading ? (
          <ActivityIndicator color="#2E7D32" />
        ) : statistics ? (
          <View style={styles.statsGrid}>
            <View style={styles.statCard}>
              <Text style={styles.statValue}>{statistics.total_journeys}</Text>
              <Text style={styles.statLabel}>Trajets</Text>
            </View>
            <View style={styles.statCard}>
              <Text style={styles.statValue}>
                {statistics.total_distance_km.toFixed(1)}
              </Text>
              <Text style={styles.statLabel}>km parcourus</Text>
            </View>
            <View style={[styles.statCard, styles.scoreCard]}>
              <Text style={[styles.statValue, styles.scoreValue]}>
                {statistics.total_score}
              </Text>
              <Text style={styles.statLabel}>Points</Text>
            </View>
          </View>
        ) : (
          <Text style={styles.noStats}>Aucune statistique disponible</Text>
        )}
      </View>

      {/* Validated Journeys Link */}
      <TouchableOpacity
        style={styles.linkButton}
        onPress={() => navigation.navigate('ValidatedJourneys')}>
        <Text style={styles.linkText}>Voir mes trajets valides</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  welcomeText: {
    fontSize: 14,
    color: '#666',
  },
  username: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
  },
  logoutButton: {
    padding: 8,
  },
  logoutText: {
    color: '#F44336',
    fontWeight: '500',
  },
  section: {
    padding: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 12,
  },
  detectionCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  statusDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    marginRight: 8,
  },
  statusActive: {
    backgroundColor: '#4CAF50',
  },
  statusInactive: {
    backgroundColor: '#9E9E9E',
  },
  statusText: {
    fontSize: 16,
    color: '#333',
  },
  button: {
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
  },
  startButton: {
    backgroundColor: '#2E7D32',
  },
  stopButton: {
    backgroundColor: '#F44336',
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  pendingCard: {
    backgroundColor: '#FFF3E0',
    borderRadius: 12,
    padding: 20,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#FFB74D',
  },
  pendingCount: {
    fontSize: 48,
    fontWeight: 'bold',
    color: '#E65100',
  },
  pendingLabel: {
    fontSize: 16,
    color: '#E65100',
    marginTop: 4,
  },
  pendingHint: {
    fontSize: 12,
    color: '#FF8A65',
    marginTop: 8,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  statCard: {
    flex: 1,
    minWidth: '30%',
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  scoreCard: {
    backgroundColor: '#E8F5E9',
  },
  statValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
  },
  scoreValue: {
    color: '#2E7D32',
  },
  statLabel: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
  },
  noStats: {
    textAlign: 'center',
    color: '#999',
    padding: 20,
  },
  linkButton: {
    margin: 16,
    padding: 16,
    backgroundColor: '#fff',
    borderRadius: 12,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#2E7D32',
  },
  linkText: {
    color: '#2E7D32',
    fontSize: 16,
    fontWeight: '500',
  },
});
