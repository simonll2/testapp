/**
 * Validated Journeys Screen - List of journeys sent to backend
 */

import React, {useState, useCallback} from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  RefreshControl,
  ActivityIndicator,
} from 'react-native';
import {useFocusEffect} from '@react-navigation/native';
import {apiClient} from '../api/client';
import {JourneyRead} from '../api/types';

export default function ValidatedJourneysScreen(): JSX.Element {
  const [journeys, setJourneys] = useState<JourneyRead[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      loadJourneys();
    }, []),
  );

  const loadJourneys = async () => {
    setError(null);
    try {
      const data = await apiClient.getValidatedJourneys();
      setJourneys(data);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Erreur de chargement';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadJourneys();
    setRefreshing(false);
  };

  const getTransportLabel = (type: string): string => {
    const labels: Record<string, string> = {
      marche: 'Marche',
      velo: 'Velo',
      voiture: 'Voiture',
      transport_commun: 'Transport en commun',
    };
    return labels[type] || type;
  };

  const getTransportIcon = (type: string): string => {
    const icons: Record<string, string> = {
      marche: '🚶',
      velo: '🚴',
      voiture: '🚗',
      transport_commun: '🚌',
    };
    return icons[type] || '🚶';
  };

  const formatDate = (isoDate: string): string => {
    const date = new Date(isoDate);
    return date.toLocaleDateString('fr-FR', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const renderJourney = ({item}: {item: JourneyRead}) => (
    <View style={styles.journeyCard}>
      <View style={styles.journeyHeader}>
        <Text style={styles.transportIcon}>
          {getTransportIcon(item.transport_type)}
        </Text>
        <View style={styles.journeyInfo}>
          <Text style={styles.transportType}>
            {getTransportLabel(item.transport_type)}
          </Text>
          <Text style={styles.journeyDate}>
            {formatDate(item.time_departure)}
          </Text>
        </View>
        <View style={styles.scoreContainer}>
          <Text style={styles.scoreValue}>+{item.score_journey}</Text>
          <Text style={styles.scoreLabel}>pts</Text>
        </View>
      </View>

      <View style={styles.journeyDetails}>
        <View style={styles.detailItem}>
          <Text style={styles.detailLabel}>Distance</Text>
          <Text style={styles.detailValue}>
            {item.distance_km.toFixed(1)} km
          </Text>
        </View>
        <View style={styles.detailItem}>
          <Text style={styles.detailLabel}>Duree</Text>
          <Text style={styles.detailValue}>{item.duration_minutes} min</Text>
        </View>
        <View style={styles.detailItem}>
          <Text style={styles.detailLabel}>Source</Text>
          <Text style={styles.detailValue}>
            {item.detection_source === 'auto' ? 'Auto' : 'Manuel'}
          </Text>
        </View>
      </View>

      <View style={styles.placesContainer}>
        <Text style={styles.placeText} numberOfLines={1}>
          {item.place_departure} → {item.place_arrival}
        </Text>
      </View>
    </View>
  );

  const renderEmpty = () => (
    <View style={styles.emptyContainer}>
      <Text style={styles.emptyIcon}>📋</Text>
      <Text style={styles.emptyTitle}>Aucun trajet valide</Text>
      <Text style={styles.emptySubtitle}>
        Validez vos trajets detectes pour les voir ici
      </Text>
    </View>
  );

  const renderError = () => (
    <View style={styles.emptyContainer}>
      <Text style={styles.emptyIcon}>⚠️</Text>
      <Text style={styles.emptyTitle}>Erreur</Text>
      <Text style={styles.emptySubtitle}>{error}</Text>
    </View>
  );

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#2E7D32" />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <FlatList
        data={journeys}
        renderItem={renderJourney}
        keyExtractor={item => item.id.toString()}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        ListEmptyComponent={error ? renderError : renderEmpty}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
  },
  listContent: {
    padding: 16,
    flexGrow: 1,
  },
  journeyCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  journeyHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  transportIcon: {
    fontSize: 32,
    marginRight: 12,
  },
  journeyInfo: {
    flex: 1,
  },
  transportType: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
  },
  journeyDate: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  scoreContainer: {
    backgroundColor: '#E8F5E9',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 6,
    alignItems: 'center',
  },
  scoreValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#2E7D32',
  },
  scoreLabel: {
    fontSize: 10,
    color: '#4CAF50',
  },
  journeyDetails: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: '#eee',
  },
  detailItem: {
    alignItems: 'center',
  },
  detailLabel: {
    fontSize: 12,
    color: '#999',
  },
  detailValue: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginTop: 2,
  },
  placesContainer: {
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#eee',
  },
  placeText: {
    fontSize: 13,
    color: '#666',
    textAlign: 'center',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 60,
  },
  emptyIcon: {
    fontSize: 64,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  emptySubtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    paddingHorizontal: 32,
  },
});
