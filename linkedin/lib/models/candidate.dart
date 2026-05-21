class Candidate {
  final String name;
  final String title;
  final String? company;
  final String linkedinUrl;
  final int confidence;
  final String? profilePicUrl;
  final String? headline;
  final String? summary;
  final String? location;
  final String? email;
  final String? phone;
  final String? companyWebsite;
  final String? personalWebsite;
  final String? socialProfileUrl;
  final String? bio;

  Candidate({
    required this.name,
    required this.title,
    this.company,
    required this.linkedinUrl,
    required this.confidence,
    this.profilePicUrl,
    this.headline,
    this.summary,
    this.location,
    this.email,
    this.phone,
    this.companyWebsite,
    this.personalWebsite,
    this.socialProfileUrl,
    this.bio,
  });

  Candidate copyWith({
    String? name,
    String? title,
    String? company,
    String? linkedinUrl,
    int? confidence,
    String? profilePicUrl,
    String? headline,
    String? summary,
    String? location,
    String? email,
    String? phone,
    String? companyWebsite,
    String? personalWebsite,
    String? socialProfileUrl,
    String? bio,
  }) {
    return Candidate(
      name: name ?? this.name,
      title: title ?? this.title,
      company: company ?? this.company,
      linkedinUrl: linkedinUrl ?? this.linkedinUrl,
      confidence: confidence ?? this.confidence,
      profilePicUrl: profilePicUrl ?? this.profilePicUrl,
      headline: headline ?? this.headline,
      summary: summary ?? this.summary,
      location: location ?? this.location,
      email: email ?? this.email,
      phone: phone ?? this.phone,
      companyWebsite: companyWebsite ?? this.companyWebsite,
      personalWebsite: personalWebsite ?? this.personalWebsite,
      socialProfileUrl: socialProfileUrl ?? this.socialProfileUrl,
      bio: bio ?? this.bio,
    );
  }

  factory Candidate.fromJson(Map<String, dynamic> json) {
    return Candidate(
      name: json['name'] as String? ?? '',
      title: json['title'] as String? ?? '',
      company: json['company'] as String?,
      linkedinUrl: json['linkedinUrl'] as String? ?? '',
      confidence: json['confidence'] is int
          ? json['confidence'] as int
          : int.tryParse('${json['confidence']}') ?? 0,
      profilePicUrl: json['profilePicUrl'] as String?,
      headline: json['headline'] as String?,
      summary: json['summary'] as String?,
      location: json['location'] as String?,
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      companyWebsite: json['companyWebsite'] as String?,
      personalWebsite: json['personalWebsite'] as String?,
      socialProfileUrl: json['socialProfileUrl'] as String?,
      bio: json['bio'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'name': name,
      'title': title,
      'company': company,
      'linkedinUrl': linkedinUrl,
      'confidence': confidence,
      'profilePicUrl': profilePicUrl,
      'headline': headline,
      'summary': summary,
      'location': location,
      'email': email,
      'phone': phone,
      'companyWebsite': companyWebsite,
      'personalWebsite': personalWebsite,
      'socialProfileUrl': socialProfileUrl,
      'bio': bio,
    };
  }
}
